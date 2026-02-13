package services

import aws.{Clients, Iam}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.toTraverseOps
import com.gu.janus.model.{AwsAccount, ProvisionedRole}
import fs2.Stream
import logic.DeveloperPolicies
import models.{
  AwsAccountDeveloperPolicyStatus,
  DeveloperPolicy,
  DeveloperPolicySnapshot,
  FailureSnapshot
}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.{ListPoliciesRequest, Policy}
import software.amazon.awssdk.services.sts.StsClient

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/** For use where we just need to know which [[DeveloperPolicy]]s are part of a
  * particular [[ProvisionedRole]].
  */
trait DeveloperPolicyFinder {
  def getDeveloperPoliciesByProvisionedRole(
      role: ProvisionedRole
  ): List[DeveloperPolicy]
}

/** For use where we want to know the status of the [[ProvisionedRole]] data
  * we've cached.
  */
trait DeveloperPolicyStatusManager {
  val fetchEnabled: Boolean
  val fetchRate: FiniteDuration
  def getCacheStatus: Map[AwsAccount, AwsAccountDeveloperPolicyStatus]
}

/** Fetches and keeps a cache of [[DeveloperPolicy]]s that have a
  * [[com.gu.janus.model.ProvisionedRole]] tag attached to them. Policy data is
  * fetched from across multiple accounts.
  *
  * @param accounts
  *   AWS accounts to search
  * @param config
  *   Needed to build the role that will be assumed to fetch the data
  * @param sts
  *   Needed for cross-account access by role assumption
  */
class DeveloperPolicyCachingService(
    accounts: Set[AwsAccount],
    config: Configuration,
    sts: StsClient
) extends DeveloperPolicyFinder
    with DeveloperPolicyStatusManager {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override val fetchEnabled: Boolean =
    config.get[Boolean]("provisionedRoles.fetch.enabled")
  override val fetchRate: FiniteDuration =
    config.get[FiniteDuration]("provisionedRoles.fetch.rate")

  private val discoverablePath =
    config.get[String]("provisionedRoles.discoverablePath")

  private val provisionedRoleTagKey =
    config.get[String]("provisionedRoles.tagKey")
  private val friendlyNameTagKey =
    config.get[String]("provisionedRoles.friendlyNameTagKey")
  private val descriptionTagKey =
    config.get[String]("provisionedRoles.descriptionTagKey")

  // TrieMap is Scala's default concurrent Map implementation
  private val cache = new TrieMap[AwsAccount, AwsAccountDeveloperPolicyStatus]()
  accounts.foreach { account =>
    cache.update(account, AwsAccountDeveloperPolicyStatus.empty)
  }

  private val accountIams: Map[AwsAccount, IamClient] =
    accounts.map { account =>
      val iam =
        Clients.developerPolicyReadingIam(
          account,
          sts,
          config,
          "DeveloperPolicyReader"
        )
      account -> iam
    }.toMap

  def startPolling(): Stream[IO, Unit] =
    if (fetchEnabled) {
      Stream.eval(
        logger.info(
          s"Developer policy caching enabled with fetch rate: $fetchRate"
        )
      ) ++ Stream
        // do first fetch immediately
        .emit(())
        // then periodically
        .append(Stream.awakeEvery[IO](fetchRate))
        .flatMap(_ => Stream.emits(accountIams.toList))
        .evalMap((account, iam) =>
          fetchFromAccount(account, iam)
            .map(status => cache.update(account, status))
        )
        .handleErrorWith { err =>
          Stream.eval(
            logger.error(err)("Failed to refresh developer policy cache")
          )
        }
    } else
      Stream.eval(logger.warn("Developer policy caching has been disabled!"))

  override def getDeveloperPoliciesByProvisionedRole(
      role: ProvisionedRole
  ): List[DeveloperPolicy] =
    DeveloperPolicies.getDeveloperPoliciesByProvisionedRole(
      cache.readOnlySnapshot().toMap,
      role
    )

  override def getCacheStatus
      : Map[AwsAccount, AwsAccountDeveloperPolicyStatus] =
    cache.readOnlySnapshot().toMap

  def shutdown(): IO[Unit] =
    accountIams.values.toList.traverse(iam => IO(iam.close())).void

  private def fetchFromAccount(
      account: AwsAccount,
      iam: IamClient
  ): IO[AwsAccountDeveloperPolicyStatus] =
    (for {
      _ <- logger.debug(
        s"Fetching developer policies from account '${account.name}'..."
      )
      policyListRequest = ListPoliciesRequest.builder
        .pathPrefix(discoverablePath)
        .build()
      awsPolicies <- Iam.listPolicies(iam, policyListRequest)
      policies <- awsPolicies.traverse(policy =>
        fetchDeveloperPolicy(account, iam, policy)
      )
      now <- IO.realTimeInstant
      _ <- logger.debug(
        s"Fetched ${policies.size} developer policies from account '${account.name}'."
      )
    } yield AwsAccountDeveloperPolicyStatus.success(
      DeveloperPolicySnapshot(policies, now)
    )).handleErrorWith(err =>
      for {
        now <- IO.realTimeInstant
        _ <- logger.error(err)(
          s"Failed to fetch developer policies from account '${account.name}'"
        )
      } yield AwsAccountDeveloperPolicyStatus.failure(
        cachedPolicySnapshot = cache.get(account).flatMap(_.policySnapshot),
        failureStatus = FailureSnapshot(err.getMessage, now)
      )
    )

  private def fetchDeveloperPolicy(
      account: AwsAccount,
      iam: IamClient,
      policy: Policy
  ): IO[DeveloperPolicy] =
    for {
      tags <- Iam.listPolicyTags(iam, policy)
      policy <- IO.fromOption(
        DeveloperPolicies.toDeveloperPolicy(
          account,
          policy,
          tags,
          provisionedRoleTagKey,
          friendlyNameTagKey,
          descriptionTagKey
        )
      )(
        new Exception(
          s"Required tag '$provisionedRoleTagKey' not found on policy ${policy.arn}"
        )
      )
    } yield policy
}

object DeveloperPolicyCachingService {

  /** Convenience method to start and manage lifecycle of the caching service.
    */
  def start(
      appLifecycle: ApplicationLifecycle,
      accounts: Set[AwsAccount],
      config: Configuration,
      sts: StsClient
  )(using ExecutionContext): DeveloperPolicyCachingService = {
    val service = new DeveloperPolicyCachingService(accounts, config, sts)
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
