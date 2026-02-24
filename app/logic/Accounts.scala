package logic

import com.gu.janus.model.{ACL, AwsAccount}
import models.*

import scala.util.Try

object Accounts {

  private[logic] def lookupAccountDeveloperPolicies(
      statuses: Map[AwsAccount, AwsAccountDeveloperPolicyStatus],
      account: AwsAccount,
      accountIdMaybe: Try[String]
  ): Set[DeveloperPolicy] = (for {
    accountId <- accountIdMaybe.toOption
    status <- statuses.get(account)
    snapshot <- status.policySnapshot
  } yield snapshot.policies.toSet)
    .getOrElse(Set.empty)

  def getAccountPoliciesAndStatus(
      statuses: Map[AwsAccount, AwsAccountDeveloperPolicyStatus]
  ): Map[String, (List[DeveloperPolicy], Option[String])] = statuses
    .map { (k, v) =>
      k.name -> (
        v.policySnapshot.map(_.policies).getOrElse(Nil),
        v.failureStatus.map(_.failure)
      )
    }

  def successfulPoliciesForThisAccount(
      statuses: Map[AwsAccount, AwsAccountDeveloperPolicyStatus],
      account: String
  ): List[DeveloperPolicy] = (for {
    status <- statuses.find(_._1.authConfigKey == account)
    snapshot <- status._2.policySnapshot
  } yield snapshot.policies)
    .getOrElse(Nil)

  def errorPoliciesForThisAccount(
      statuses: Map[AwsAccount, AwsAccountDeveloperPolicyStatus],
      account: String
  ): Option[String] = for {
    accountAndStatus <- statuses.find(_._1.authConfigKey == account)
    status = accountAndStatus._2
    failureSnapshot <- status.failureStatus
  } yield failureSnapshot.failure

  def accountOwnerInformation(
      policyStatuses: Map[AwsAccount, AwsAccountDeveloperPolicyStatus],
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
        lookupAccountDeveloperPolicies(
          policyStatuses,
          awsAccount,
          accountIdMaybe
        ),
        errorPoliciesForThisAccount(policyStatuses, awsAccount.authConfigKey)
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
