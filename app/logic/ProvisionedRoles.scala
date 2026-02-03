package logic

import com.gu.janus.model.{AwsAccount, ProvisionedRole}
import models.{AwsAccountIamRoleInfoStatus, IamRoleInfo}
import software.amazon.awssdk.services.iam.model.{Role, Tag}

import java.net.URLEncoder

object ProvisionedRoles {

  def getIamRolesByProvisionedRole(
      cache: Map[AwsAccount, AwsAccountIamRoleInfoStatus],
      role: ProvisionedRole
  ): List[IamRoleInfo] =
    cache.values
      .flatMap(_.roleSnapshot)
      .flatMap(_.roles)
      .filter(_.provisionedRoleTagValue == role.iamRoleTag)
      .toList

  def toRoleInfo(
      account: AwsAccount,
      role: Role,
      tags: Set[Tag],
      provisionedRoleTagKey: String,
      friendlyNameTagKey: String,
      descriptionTagKey: String
  ): Option[IamRoleInfo] =
    for {
      provisionedRoleTag <- tags.find(_.key() == provisionedRoleTagKey)
    } yield IamRoleInfo(
      roleArnString = role.arn(),
      roleName = role.roleName(),
      provisionedRoleTagValue = provisionedRoleTag.value(),
      friendlyName = tags.find(_.key() == friendlyNameTagKey).map(_.value()),
      description = tags.find(_.key() == descriptionTagKey).map(_.value()),
      account
    )

  /** To get a URL-safe slug for a provisioned role, we use the IAM role name.
    *
    * Janus permission IDs are created from the permission label, combined with
    * the account name. We take the same approach here, but use the (unique to
    * each AWS account) role name.
    *
    * To ensure this slug does not clash with a Janus permission, we use a
    * prefix to namespace provisioned roles.
    *
    * We URL-encode the role name to ensure it's URL-safe while preserving
    * uniqueness (avoiding collisions from AWS role names that differ only in
    * special characters like `.`, `,`, `+`, `@`, etc.).
    */
  def iamRoleInfoSlug(
      roleName: String,
      account: AwsAccount
  ): String = {
    val encodedRoleName = URLEncoder.encode(roleName, "UTF-8")
    s"$PROVISIONED_ROLE_NAMESPACE_PREFIX-${account.authConfigKey}-$encodedRoleName"
  }
  private[logic] val PROVISIONED_ROLE_NAMESPACE_PREFIX = "iam-"

  /** Builds a working AWS console link from the role's ARN. These links require
    * a valid console session.
    *
    * Note: For the link to work this role name must not be URL-encoded.
    */
  def provisionedRoleLink(roleName: String): String =
    s"https://console.aws.amazon.com/iam/home#/roles/details/$roleName"
}
