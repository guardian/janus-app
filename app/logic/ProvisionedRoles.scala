package logic

import com.gu.janus.model.{AwsAccount, ProvisionedRole}
import models.{AwsAccountIamRoleInfoStatus, IamRoleInfo}
import software.amazon.awssdk.arns.Arn
import software.amazon.awssdk.services.iam.model.{Role, Tag}
import scala.jdk.OptionConverters.RichOptional

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
      provisionedRoleTagValue = provisionedRoleTag.value(),
      friendlyName = tags.find(_.key() == friendlyNameTagKey).map(_.value()),
      description = tags.find(_.key() == descriptionTagKey).map(_.value()),
      account
    )

  /** Returns the role name for a Janus provisioned role.
    *
    * Note that the ARN "qualifier" does not return the first path segment (`gu`
    * in this case). So rather than searching for and stripping
    * /gu/janus/discoverable/ we search for and strip janus/discoverable/.
    */
  def provisionedRoleNameFromArn(roleArn: Arn): Option[String] =
    roleArn.resource
      .qualifier()
      .toScala
      .flatMap { qualifier =>
        println(s"${roleArn.toString} => $qualifier")
        if (qualifier.startsWith("janus/discoverable/"))
          Some(qualifier.replace("janus/discoverable/", ""))
        else
          None
      }

  /** Builds a working AWS console link from the role's ARN. These links require
    * a valid console session.
    */
  def provisionedRoleLinkFromArn(roleArn: Arn): Option[String] =
    provisionedRoleNameFromArn(roleArn).map { roleName =>
      s"https://console.aws.amazon.com/iam/home#/roles/details/$roleName"
    }
}
