package logic

import com.gu.janus.model.{ACL, AwsAccount}
import models.*

import scala.util.{Failure, Success, Try}

object Accounts {

  private type RolesStatuses = Map[AwsAccount, AwsAccountIamRoleInfoStatus]

  def lookupAccountRoles(
      rolesStatuses: RolesStatuses,
      account: AwsAccount,
      accountIdMaybe: Try[String]
  ): Set[IamRoleInfo] =
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

  def getAccountRolesAndStatus(
      rolesStatuses: RolesStatuses
  ): Map[String, (List[IamRoleInfo], Option[String])] = rolesStatuses
    .map { (k, v) =>
      k.name -> (
        v.roleSnapshot.map(_.roles).getOrElse(List.empty),
        v.failureStatus.map(_.failure)
      )
    }

  def successfulRolesForThisAccount(
      rolesStatuses: RolesStatuses,
      account: String
  ): List[IamRoleInfo] =
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

  def errorRolesForThisAccount(
      rolesStatuses: RolesStatuses,
      account: String
  ): Option[String] = {
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

  def accountOwnerInformation(
      rolesStatuses: RolesStatuses,
      accounts: Set[AwsAccount],
      access: ACL
  )(
      lookupAccountNumber: AwsAccount => Try[String]
  ): Set[AccountInfo] = accounts
    .map { awsAccount =>
      val accountIdMaybe = lookupAccountNumber(awsAccount)
      AccountInfo(
        awsAccount,
        accountPermissions(awsAccount, access),
        accountIdMaybe,
        lookupAccountRoles(rolesStatuses, awsAccount, accountIdMaybe),
        errorRolesForThisAccount(rolesStatuses, awsAccount.authConfigKey)
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

}
