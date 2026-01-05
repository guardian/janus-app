package aws

import cats.effect.IO
import com.gu.janus.model.AwsAccount
import conf.Config
import data.ProvisionedRoleCache
import fs2.Stream
import play.api.{Configuration, Logging}
import software.amazon.awssdk.regions.Region.US_EAST_1
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
import software.amazon.awssdk.services.sts.model.PolicyDescriptorType

import scala.concurrent.duration.FiniteDuration

/** Fetches AWS IAM roles that have a [[com.gu.janus.model.ProvisionedRole]] tag
  * attached to them. Role data is fetched from across multiple accounts.
  *
  * @param accounts
  *   AWS accounts to search
  * @param config
  *   Needed to build the role that will be assumed to fetch the data
  * @param sts
  *   Needed for cross-account access by role assumption
  * @param cache
  *   Holds the data found
  * @param fetchRate
  *   How often to refresh data held in the cache
  */
class ProvisionedRoleFetcher(
    accounts: Set[AwsAccount],
    config: Configuration,
    sts: StsClient,
    cache: ProvisionedRoleCache,
    fetchRate: FiniteDuration
) extends Logging {

  private val accountFetchers: Map[AwsAccount, SingleAccountRoleFetcher] =
    accounts.map { account =>
      val iam = accountIam(account)
      account -> new SingleAccountRoleFetcher(account, iam, cache)
    }.toMap

  def startPolling(): Stream[IO, Unit] = {
    Stream
      // do first fetch immediately
      .emit(())
      // then periodically
      .append(Stream.awakeEvery[IO](fetchRate))
      .evalMap { _ =>
        Stream
          .emits(accounts.toSeq)
          .evalMap(account =>
            accountFetchers(account).fetchDataAndUpdateCache()
          )
          .compile
          .drain
      }
      .handleErrorWith { err =>
        Stream.eval(
          IO(logger.error("Failed to refresh provisioned role cache", err))
        )
      }
  }

  def close(): IO[Unit] =
    IO {
      accountFetchers.values.foreach(_.close())
      logger.info("Closed all provisioned role fetcher resources.")
    }

  private def accountIam(account: AwsAccount): IamAsyncClient = {
    val roleArn = Config.roleArn(account.authConfigKey, config)
    val roleSessionName = s"ProvisionedRoleReader-${account.authConfigKey}"

    // Auto-refreshes creds used by IAM client
    val credentialsProvider = StsAssumeRoleCredentialsProvider.builder
      .stsClient(sts)
      .asyncCredentialUpdateEnabled(true)
      .refreshRequest(builder =>
        builder
          .roleArn(roleArn)
          .roleSessionName(roleSessionName)
          .policyArns(
            PolicyDescriptorType.builder
              .arn("arn:aws:iam::aws:policy/IAMReadOnlyAccess")
              .build()
          )
          // 15 minutes, which is minimum AWS allows
          .durationSeconds(900)
          .build()
      )
      .build()

    IamAsyncClient.builder
      .credentialsProvider(credentialsProvider)
      // Builder seems to need a region even though IAM is global
      .region(US_EAST_1)
      .build()
  }
}
