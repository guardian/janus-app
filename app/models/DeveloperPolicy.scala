package models

import com.gu.janus.model.{AwsAccount, Permission}
import software.amazon.awssdk.arns.Arn

import java.time.Instant
import scala.util.Try

/** Holds the data required to manage an IAM policy that's part of a
  * [[com.gu.janus.model.DeveloperPolicyGrant]]. There is a 1..* relationship
  * between a DeveloperPolicyGrant and a set of DeveloperPolicies. The source of
  * the DeveloperPolicy is a managed IAM policy fetched from AWS at runtime.
  *
  * @param policyArn
  *   ARN
  * @param policyName
  *   Name of source AWS managed policy
  * @param policyGrantId
  *   Corresponds to [[com.gu.janus.model.DeveloperPolicyGrant.id]]
  * @param sourceRepo
  *   Repo holding cloudformation template for the policy
  * @param stack
  *   Name of stack containing the policy
  * @param stage
  *   Deployment stage where resources are available
  * @param friendlyName
  *   Short description for display in Janus UI
  * @param account
  *   AWS account hosting the policy
  */
case class DeveloperPolicy(
    policyArn: Arn,
    policyName: String,
    policyGrantId: String,
    sourceRepo: String,
    stack: String,
    stage: String,
    friendlyName: String,
    account: AwsAccount
)

object DeveloperPolicy {
  def apply(
      policyArnString: String,
      policyName: String,
      policyGrantId: String,
      sourceRepo: String,
      stack: String,
      stage: String,
      friendlyName: String,
      account: AwsAccount
  ): DeveloperPolicy = DeveloperPolicy(
    Arn.fromString(policyArnString),
    policyName,
    policyGrantId,
    sourceRepo,
    stack,
    stage,
    friendlyName,
    account
  )
}

/** A snapshot of a list of policies at the given time. */
case class DeveloperPolicySnapshot(
    policies: List[DeveloperPolicy],
    timestamp: Instant
)

/** A failure and when it was recorded. */
case class FailureSnapshot(
    failure: String,
    timestamp: Instant
)

/** A list of explicit permissions for a user */
case class UserPermissions(userName: String, permissions: Set[Permission])

case class AccountInfo(
    account: AwsAccount,
    permissions: List[UserPermissions],
    accountNumberTry: Try[String],
    policies: Set[DeveloperPolicy],
    policyError: Option[String]
)

/** Status of [[DeveloperPolicy]] data fetched from a single AWS account. */
case class AwsAccountDeveloperPolicyStatus(
    policySnapshot: Option[DeveloperPolicySnapshot],
    failureStatus: Option[FailureSnapshot]
)

enum DeveloperPolicyCacheStatus {
  case Success, Empty, Disabled, Failure
}

object AwsAccountDeveloperPolicyStatus {

  /** Convenience constructor for a successful data fetch. */
  def success(
      policySnapshot: DeveloperPolicySnapshot
  ): AwsAccountDeveloperPolicyStatus =
    AwsAccountDeveloperPolicyStatus(Some(policySnapshot), failureStatus = None)

  /** Convenience constructor for when a data fetch fails. In this case we use
    * the last cached version of data (if it exists) and record the failure so
    * that we have a complete status record.
    */
  def failure(
      cachedPolicySnapshot: Option[DeveloperPolicySnapshot],
      failureStatus: FailureSnapshot
  ): AwsAccountDeveloperPolicyStatus =
    AwsAccountDeveloperPolicyStatus(cachedPolicySnapshot, Some(failureStatus))

  def empty: AwsAccountDeveloperPolicyStatus =
    AwsAccountDeveloperPolicyStatus(None, None)
}
