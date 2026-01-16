package services

import aws.{Clients, Iam}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.toTraverseOps
import com.gu.janus.model.{AwsAccount, ProvisionedRole}
import fs2.Stream
import logic.ProvisionedRoles
import models.{
  AwsAccountIamRoleInfoStatus,
  FailureSnapshot,
  IamRoleInfo,
  IamRoleInfoSnapshot
}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.{ListRolesRequest, Role}
import software.amazon.awssdk.services.sts.StsClient

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/** For use where we just need to know which IAM roles are part of a particular
  * [[ProvisionedRole]].
  */
trait ProvisionedRoleFinder {
  def getIamRolesByProvisionedRole(role: ProvisionedRole): List[IamRoleInfo]
}

/** For use where we want to know the status of the [[ProvisionedRole]] data
  * we've cached.
  */
trait ProvisionedRoleStatusManager {
  def getCacheStatus: Map[AwsAccount, AwsAccountIamRoleInfoStatus]
}

/** Fetches and keeps a cache of AWS IAM roles that have a
  * [[com.gu.janus.model.ProvisionedRole]] tag attached to them. Role data is
  * fetched from across multiple accounts.
  *
  * @param accounts
  *   AWS accounts to search
  * @param config
  *   Needed to build the role that will be assumed to fetch the data
  * @param sts
  *   Needed for cross-account access by role assumption
  */
class ProvisionedRoleCachingService(
    accounts: Set[AwsAccount],
    config: Configuration,
    sts: StsClient
) extends ProvisionedRoleFinder
    with ProvisionedRoleStatusManager {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val fetchEnabled =
    config.get[Boolean]("provisionedRoles.fetch.enabled")
  private val fetchRate =
    config.get[FiniteDuration]("provisionedRoles.fetch.rate")

  private val provisionedRoleTagKey =
    config.get[String]("provisionedRoles.tagKey")
  private val friendlyNameTagKey =
    config.get[String]("provisionedRoles.friendlyNameTagKey")
  private val descriptionTagKey =
    config.get[String]("provisionedRoles.descriptionTagKey")

  private val roleListRequest = ListRolesRequest.builder
    .pathPrefix(config.get("provisionedRoles.discoverablePath"))
    .build()

  // TrieMap is Scala's default concurrent Map implementation
  private val cache = new TrieMap[AwsAccount, AwsAccountIamRoleInfoStatus]()

  private val accountIams: Map[AwsAccount, IamClient] =
    accounts.map { account =>
      val iam =
        Clients.provisionedRoleReadingIam(
          account,
          sts,
          config,
          "ProvisionedRoleReader"
        )
      account -> iam
    }.toMap

  def startPolling(): Stream[IO, Unit] =
    if (fetchEnabled) {
      Stream
        // do first fetch immediately
        .emit(())
        // then periodically
        .append(Stream.awakeEvery[IO](fetchRate))
        .flatMap(_ => Stream.emits(accounts.toList))
        .evalMap(account =>
          fetchFromAccount(account)
            .map(status => cache.update(account, status))
        )
        .handleErrorWith { err =>
          Stream.eval(
            logger.error(err)("Failed to refresh provisioned role cache")
          )
        }
    } else
      Stream.eval(logger.warn("Provisioned role caching has been disabled!"))

  override def getIamRolesByProvisionedRole(
      role: ProvisionedRole
  ): List[IamRoleInfo] =
    ProvisionedRoles.getIamRolesByProvisionedRole(
      cache.readOnlySnapshot().toMap,
      role
    )

  override def getCacheStatus: Map[AwsAccount, AwsAccountIamRoleInfoStatus] =
    cache.readOnlySnapshot().toMap

  def shutdown(): IO[Unit] =
    accountIams.values.toList.traverse(iam => IO(iam.close())).map(_ => ())

  private def fetchFromAccount(
      account: AwsAccount
  ): IO[AwsAccountIamRoleInfoStatus] =
    (for {
      _ <- logger.info(
        s"Fetching provisioned roles from account '${account.name}'..."
      )
      iam <- IO.fromOption(accountIams.get(account))(
        new Exception(s"IAM client for account '${account.name}' not found")
      )
      roles <- Iam.listRoles(iam, roleListRequest)
      roleInfos <- roles.traverse(role => fetchRoleInfo(iam, role))
      now <- IO.realTimeInstant
      _ <- logger.info(
        s"Fetched ${roleInfos.size} provisioned roles from account '${account.name}'."
      )
    } yield AwsAccountIamRoleInfoStatus.success(
      IamRoleInfoSnapshot(roleInfos, now)
    )).handleErrorWith(err =>
      for {
        now <- IO.realTimeInstant
        _ <- logger.error(err)(
          s"Failed to fetch provisioned roles from account '${account.name}'"
        )
      } yield AwsAccountIamRoleInfoStatus.failure(
        cachedRoleSnapshot = cache.get(account).flatMap(_.roleSnapshot),
        failureStatus = FailureSnapshot(err.getMessage, now)
      )
    )

  private def fetchRoleInfo(iam: IamClient, role: Role): IO[IamRoleInfo] =
    for {
      tags <- Iam.listRoleTags(iam, role)
      roleInfo <- IO.fromOption(
        ProvisionedRoles.toRoleInfo(
          role,
          tags,
          provisionedRoleTagKey,
          friendlyNameTagKey,
          descriptionTagKey
        )
      )(
        new Exception(
          s"Required tag '$provisionedRoleTagKey' not found on role ${role.arn()}"
        )
      )
    } yield roleInfo
}

object ProvisionedRoleCachingService {

  /** Convenience method to start and manage lifecycle of the caching service.
    */
  def start(
      appLifecycle: ApplicationLifecycle,
      accounts: Set[AwsAccount],
      config: Configuration,
      sts: StsClient
  )(using ExecutionContext): ProvisionedRoleCachingService = {
    val service = new ProvisionedRoleCachingService(accounts, config, sts)
    val cancellationToken = service
      .startPolling()
      .compile
      .drain
      .unsafeRunCancelable()
    appLifecycle.addStopHook(() =>
      for {
        _ <- cancellationToken()
        _ <- service.shutdown().unsafeToFuture()
      } yield ()
    )
    service
  }
}
