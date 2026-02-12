package logic

import com.gu.janus.model.{AwsAccount, ProvisionedRole}
import models.{AwsAccountDeveloperPolicyStatus, DeveloperPolicy}
import software.amazon.awssdk.services.iam.model.{Policy, Tag}

import java.net.URLEncoder

object DeveloperPolicies {

  def getDeveloperPoliciesByProvisionedRole(
      cache: Map[AwsAccount, AwsAccountDeveloperPolicyStatus],
      role: ProvisionedRole
  ): List[DeveloperPolicy] =
    cache.values
      .flatMap(_.policySnapshot)
      .flatMap(_.policies)
      .filter(_.provisionedRoleTagValue == role.iamRoleTag)
      .toList

  def toDeveloperPolicy(
      account: AwsAccount,
      policy: Policy,
      tags: Set[Tag],
      provisionedRoleTagKey: String,
      friendlyNameTagKey: String,
      descriptionTagKey: String
  ): Option[DeveloperPolicy] =
    for {
      provisionedRoleTag <- tags.find(_.key == provisionedRoleTagKey)
    } yield DeveloperPolicy(
      policyArnString = policy.arn,
      policyName = policy.policyName,
      provisionedRoleTagValue = provisionedRoleTag.value,
      friendlyName = tags.find(_.key == friendlyNameTagKey).map(_.value),
      description = tags.find(_.key == descriptionTagKey).map(_.value),
      account
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
  def developerPolicySlug(
      policyName: String,
      account: AwsAccount
  ): String = {
    val encodedPolicyName = URLEncoder.encode(policyName, "UTF-8")
    s"$DEVELOPER_POLICY_NAMESPACE_PREFIX-${account.authConfigKey}-$encodedPolicyName"
  }
  private[logic] val DEVELOPER_POLICY_NAMESPACE_PREFIX = "iam-"

  /** Builds a working AWS console link from the policy's name. These links
    * require a valid console session.
    *
    * Note: For the link to work this policy name must not be URL-encoded.
    */
  def developerPolicyLink(policyName: String): String =
    s"https://console.aws.amazon.com/iam/home#/policies/details/$policyName"
}
