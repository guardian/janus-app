package logic

import com.gu.janus.model.{AwsAccount, DeveloperPolicyGrant, Permission}
import models.{
  AwsAccountDeveloperPolicyStatus,
  DeveloperPolicy,
  DeveloperPolicyCacheStatus
}
import software.amazon.awssdk.services.iam.model.Policy

import java.net.URLEncoder

object DeveloperPolicies {

  /** Creates a DeveloperPolicy from an AWS IAM managed policy if it's possible.
    *
    * @param account
    *   AWS account holding the source policy.
    * @param policy
    *   Source AWS IAM managed policy
    */
  def toDeveloperPolicy(
      account: AwsAccount,
      policy: Policy
  ): Option[DeveloperPolicy] = {
    /* Incoming AWS managed policy path should have structure:
     * /developer-policy/guardian/<repo-name>/<stack>/<stage>/<grant-id>/
     */
    val pathParts = policy.path.split('/')
    for {
      // We expect 7 parts including empty string at start
      pathSections <- Option.when(pathParts.length == 7)(pathParts.tail)
      policyGrantId <- pathSections.lift(5).filter(!_.isBlank)
      description <- Option(policy.description).filter(!_.isBlank)
    } yield DeveloperPolicy(
      policyArnString = policy.arn,
      policyName = policy.policyName,
      policyGrantId,
      description,
      account
    )
  }

  def toPermission(
      policy: DeveloperPolicy,
      grant: DeveloperPolicyGrant
  ): Permission =
    Permission.fromManagedPolicyArns(
      account = policy.account,
      label = developerPolicySlug(policy.policyName),
      description = policy.description,
      managedPolicyArns = List(policy.policyArn.toString),
      shortTerm = grant.shortTerm
    )

  /** To get a URL-safe slug for a [[DeveloperPolicy]], we use the IAM policy
    * name.
    *
    * Janus permission IDs are created from the permission label, combined with
    * the account name. We take the same approach here, but use the (unique to
    * each AWS account) policy name.
    *
    * To ensure this slug does not clash with a Janus permission, we use a
    * prefix to namespace developer policies.
    *
    * We URL-encode the policy name to ensure it's URL-safe while preserving
    * uniqueness (avoiding collisions from AWS policy names that differ only in
    * special characters like `.`, `,`, `+`, `@`, etc.).
    */
  private[logic] def developerPolicySlug(policyName: String): String = {
    val encodedPolicyName = URLEncoder.encode(policyName, "UTF-8")
    s"$DEVELOPER_POLICY_NAMESPACE_PREFIX$encodedPolicyName"
  }
  private[logic] val DEVELOPER_POLICY_NAMESPACE_PREFIX = "iam-"

  /** Builds a working AWS console link from a [[DeveloperPolicy]]. These links
    * require a valid console session.
    */
  def developerPolicyLink(policy: DeveloperPolicy): String =
    s"https://console.aws.amazon.com/iam/home#/policies/${policy.policyArn}"

  def lookupDeveloperPolicyCacheStatus(
      accountAndStatuses: Map[AwsAccount, AwsAccountDeveloperPolicyStatus],
      serviceEnabled: Boolean
  ): DeveloperPolicyCacheStatus = {
    val failureExists = accountAndStatuses.exists { case (_, status) =>
      status.failureStatus.isDefined
    }
    val emptyExists = accountAndStatuses.exists { case (_, status) =>
      status.policySnapshot.isEmpty
    }
    if (failureExists) DeveloperPolicyCacheStatus.Failure
    else if (!serviceEnabled) DeveloperPolicyCacheStatus.Disabled
    else if (emptyExists) DeveloperPolicyCacheStatus.Empty
    else DeveloperPolicyCacheStatus.Success
  }
}
