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
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logging}
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.{ListRolesRequest, Role}
import software.amazon.awssdk.services.sts.StsClient

import java.time.{Clock, Instant}
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
  * @param clock
  *   Gives us definite time
  */
class ProvisionedRoleCachingService(
    accounts: Set[AwsAccount],
    config: Configuration,
    sts: StsClient,
    clock: Clock = Clock.systemUTC()
) extends ProvisionedRoleFinder
    with ProvisionedRoleStatusManager
    with Logging {

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
          fetchFromAccount(account, accountIams(account))
            .map(status => cache.update(account, status))
        )
        .handleErrorWith { err =>
          Stream.eval(
            IO(logger.error("Failed to refresh provisioned role cache", err))
          )
        }
    } else
      Stream.eval(
        IO(logger.warn("Provisioned role caching has been disabled!"))
      )

  override def getIamRolesByProvisionedRole(role: ProvisionedRole): List[IamRoleInfo] =
    ProvisionedRoles.getIamRolesByProvisionedRole(
      cache.readOnlySnapshot().toMap,
      role
    )

  override def getCacheStatus: Map[AwsAccount, AwsAccountIamRoleInfoStatus] =
    cache.readOnlySnapshot().toMap

  def shutdown(): IO[Unit] =
    accountIams.values.toList.traverse(iam => IO(iam.close())).map(_ => ())

  private def fetchFromAccount(
      account: AwsAccount,
      iam: IamClient
  ): IO[AwsAccountIamRoleInfoStatus] =
    (for {
      _ <- IO(
        logger.info(
          s"Fetching provisioned roles from account '${account.name}'..."
        )
      )
      roles <- Iam
        .listRoles(iam, roleListRequest)
        .flatMap(_.traverse(role => toRoleInfo(iam, role)))
      _ <- IO(
        logger.info(
          s"Fetched ${roles.size} provisioned roles from account '${account.name}'."
        )
      )
    } yield AwsAccountIamRoleInfoStatus.success(
      IamRoleInfoSnapshot(roles, Instant.now(clock))
    )).onError { case err =>
      IO(
        logger.error(
          s"Failed to fetch provisioned roles from account '${account.name}'",
          err
        )
      )
    }.handleErrorWith(err =>
      IO(
        AwsAccountIamRoleInfoStatus.failure(
          cachedRoleSnapshot = cache.get(account).flatMap(_.roleSnapshot),
          failureStatus = FailureSnapshot(err.getMessage, Instant.now(clock))
        )
      )
    )

  private def toRoleInfo(iam: IamClient, role: Role): IO[IamRoleInfo] =
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
