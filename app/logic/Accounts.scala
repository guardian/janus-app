package logic

import com.gu.janus.model.{ACL, AwsAccount}
import models.*

import scala.util.{Failure, Success, Try}

object Accounts {

  def lookupAccountRoles(
      rolesStatuses: Map[AwsAccount, AwsAccountIamRoleInfoStatus],
      account: AwsAccount,
      accountIdMaybe: Try[String]
  ): Set[IamRoleInfo] = (for {
    accountId <- accountIdMaybe.toOption
    accountRoleStatus <- rolesStatuses.get(account)
    roles <- accountRoleStatus.roleSnapshot
  } yield roles.roles.toSet)
    .getOrElse(Set.empty)

  def getAccountRolesAndStatus(
      rolesStatuses: Map[AwsAccount, AwsAccountIamRoleInfoStatus]
  ): Map[String, (List[IamRoleInfo], Option[String])] = rolesStatuses
    .map { (k, v) =>
      k.name -> (
        v.roleSnapshot.map(_.roles).getOrElse(Nil),
        v.failureStatus.map(_.failure)
      )
    }

  def successfulRolesForThisAccount(
      rolesStatuses: Map[AwsAccount, AwsAccountIamRoleInfoStatus],
      account: String
  ): List[IamRoleInfo] = (for {
    rolesStatus <- rolesStatuses.find(_._1.authConfigKey == account)
    roleSnapshot <- rolesStatus._2.roleSnapshot
  } yield roleSnapshot.roles)
    .getOrElse(Nil)

  def errorRolesForThisAccount(
      rolesStatuses: Map[AwsAccount, AwsAccountIamRoleInfoStatus],
      account: String
  ): Option[String] = for {
    rolesStatus <- rolesStatuses.find(_._1.authConfigKey == account)
    roleInfoStatus = rolesStatus._2
    failureSnapshot <- roleInfoStatus.failureStatus
  } yield failureSnapshot.failure

  def accountOwnerInformation(
      rolesStatuses: Map[AwsAccount, AwsAccountIamRoleInfoStatus],
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
