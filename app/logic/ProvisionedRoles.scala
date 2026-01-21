package logic

import com.gu.janus.model.{AwsAccount, ProvisionedRole}
import models.{AwsAccountIamRoleInfoStatus, IamRoleInfo}
import software.amazon.awssdk.services.iam.model.{Role, Tag}

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
}
