package logic

import com.gu.janus.model.{ACL, AwsAccount, Permission}
import models.IamRoleInfo

import scala.util.{Failure, Try}

case class UserPermissions(userName: String, permissions: Set[Permission])
case class AccountInfo(
    account: AwsAccount,
    permissions: List[UserPermissions],
    configuredRole: Try[String],
    rolesStatuses: Set[IamRoleInfo]
)

object Owners {
  def accountOwnerInformation(accounts: Set[AwsAccount], access: ACL)(
      lookupConfiguredRole: AwsAccount => Try[String],
      lookupRoles: (AwsAccount, Try[String]) => Set[IamRoleInfo]
  ): Set[AccountInfo] =
    accounts
      .map { awsAccount =>
        val accountIdMaybe = lookupConfiguredRole(awsAccount)
        AccountInfo(
          awsAccount,
          accountPermissions(awsAccount, access),
          accountIdMaybe,
          lookupRoles(awsAccount, accountIdMaybe)
        )
      }

  def accountPermissions(
      account: AwsAccount,
      acl: ACL
  ): List[UserPermissions] = {
    acl.userAccess
      .flatMap { case (username, aclEntry) =>
        val permissions = aclEntry.permissions
        if (permissions.exists(_.account == account))
          Some(
            UserPermissions(username, permissions.filter(_.account == account))
          )
        else None
      }
      .toList
      .sortBy(_.userName)
  }

  def accountIdErrors(
      accountData: Set[
        AccountInfo
      ]
  ): Set[(AwsAccount, Throwable)] = {
    accountData
      .collect { case AccountInfo(account, _, Failure(err), _) =>
        (account, err)
      }
  }
}
