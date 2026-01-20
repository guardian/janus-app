package logic

import com.gu.janus.model.{ACL, AwsAccount, Permission}
import models.{
  AwsAccountIamRoleInfoStatus,
  FailureSnapshot,
  IamRoleInfo,
  IamRoleInfoSnapshot,
  AccountInfo,
  UserPermissions
}

import scala.collection.immutable
import scala.util.{Success, Try, Failure}

trait Accounts {

  def rolesStatuses: Map[AwsAccount, AwsAccountIamRoleInfoStatus]

  def lookupAccountRoles: (AwsAccount, Try[String]) => Set[IamRoleInfo] =
    (account, accountIdMaybe) =>
      accountIdMaybe match {
        case Success(accountId) =>
          rolesStatuses.get(account) match {
            case Some(
                  AwsAccountIamRoleInfoStatus(
                    Some(IamRoleInfoSnapshot(roles, _)),
                    _
                  )
                ) =>
              roles.toSet
            case _ => Set.empty
          }
        case _ => Set.empty
      }

  def getAccountRoles
      : Map[(Option[String], String), immutable.Iterable[String]] = {
    rolesStatuses
      .flatMap { (k, v) =>
        v match {
          case AwsAccountIamRoleInfoStatus(
                Some(
                  IamRoleInfoSnapshot(iamRoles, _)
                ),
                _
              ) =>
            iamRoles.map(role =>
              ((role.friendlyName, role.provisionedRoleTagValue), k.name)
            )
          case _ => Seq.empty[((Option[String], String), String)]
        }
      }
      .groupMap(_._1)(_._2)
  }

  def getFailedAccountRoles: Map[String, immutable.Iterable[Option[String]]] = {
    rolesStatuses
      .map { (k, v) =>
        v match {
          case AwsAccountIamRoleInfoStatus(
                _,
                Some(
                  FailureSnapshot(failure, _)
                )
              ) =>
            (k.name, Some(failure))
          case _ => (k.name, None)
        }
      }
      .groupMap(_._1)(_._2)
      .filter(_._2.flatten.nonEmpty)
  }

  def successfulRolesForThisAccount(account: String): List[IamRoleInfo] = {
    rolesStatuses.find(_._1.authConfigKey == account) match {
      case Some(
            _,
            AwsAccountIamRoleInfoStatus(
              Some(IamRoleInfoSnapshot(roles, _)),
              _
            )
          ) =>
        roles
      case _ => Nil
    }
  }

  def errorRolesForThisAccount(account: String): Option[String] = {
    rolesStatuses.find(_._1.authConfigKey == account) match {
      case Some(
            _,
            AwsAccountIamRoleInfoStatus(
              _,
              Some(FailureSnapshot(failure, _))
            )
          ) =>
        Some(failure)
      case _ => None
    }
  }

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
