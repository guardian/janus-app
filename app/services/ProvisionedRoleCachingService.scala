package services

import aws.{Clients, Iam}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.toTraverseOps
import com.github.benmanes.caffeine.cache.Caffeine
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
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.iam.model.{ListRolesRequest, Role}
import software.amazon.awssdk.services.sts.StsClient

import java.time.{Clock, Instant}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*

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
// TODO: caffeine go
class ProvisionedRoleCachingService(
    accounts: Set[AwsAccount],
    config: Configuration,
    sts: StsClient,
    clock: Clock = Clock.systemUTC()
) extends ProvisionedRoleFinder
    with ProvisionedRoleStatusManager
    with Logging {

  private val fetchRate =
    config.get[FiniteDuration]("provisionedRole.fetch.rate")

  private val provisionedRoleTagKey =
    config.get[String]("provisionedRole.tagKey")
  private val friendlyNameTagKey =
    config.get[String]("provisionedRole.friendlyNameTagKey")
  private val descriptionTagKey =
    config.get[String]("provisionedRole.descriptionTagKey")

  private val roleListRequestBuilder =
    ListRolesRequest.builder.pathPrefix(
      config.get("provisionedRole.discoverablePath")
    )

  private val cache =
    Caffeine.newBuilder
      .maximumSize(1000)
      .build[AwsAccount, AwsAccountIamRoleInfoStatus]

  private val accountIams: Map[AwsAccount, IamAsyncClient] =
    accounts.map { account =>
      val iam =
        Clients.accountIam(account, sts, config, "ProvisionedRoleReader")
      account -> iam
    }.toMap

  def startPolling(): Stream[IO, Unit] = {
    Stream
      // do first fetch immediately
      .emit(())
      // then periodically
      .append(Stream.awakeEvery[IO](fetchRate))
      .evalMap { _ =>
        fetchFromAllAccounts().flatMap { fetched =>
          IO {
            cache.invalidateAll()
            cache.putAll(fetched.asJava)
          }
        }
      }
      .handleErrorWith { err =>
        Stream.eval(
          IO(logger.error("Failed to refresh provisioned role cache", err))
        )
      }
  }

  def getIamRolesByProvisionedRole(role: ProvisionedRole): List[IamRoleInfo] =
    ProvisionedRoles.getIamRolesByProvisionedRole(
      cache.asMap().asScala.toMap,
      role
    )

  def getCacheStatus: Map[AwsAccount, AwsAccountIamRoleInfoStatus] =
    cache.asMap().asScala.toMap

  def shutdown(): IO[Unit] =
    accountIams.values.toList.traverse(iam => IO(iam.close())).map(_ => ())

  private def fetchFromAllAccounts()
      : IO[Map[AwsAccount, AwsAccountIamRoleInfoStatus]] =
    accounts.toList
      .traverse { account =>
        fetchFromAccount(account, accountIams(account))
          .map(status => account -> status)
      }
      .map(_.toMap)

  private def fetchFromAccount(
      account: AwsAccount,
      iam: IamAsyncClient
  ): IO[AwsAccountIamRoleInfoStatus] =
    (for {
      _ <- IO(
        logger.info(
          s"Fetching provisioned roles from account '${account.name}'..."
        )
      )
      roles <- Iam
        .listRoles(iam, roleListRequestBuilder)
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
          cachedRoleSnapshot =
            Option(cache.getIfPresent(account)).flatMap(_.roleSnapshot),
          failureStatus = FailureSnapshot(err.getMessage, Instant.now(clock))
        )
      )
    )

  private def toRoleInfo(iam: IamAsyncClient, role: Role): IO[IamRoleInfo] =
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
