package logic

import com.gu.janus.model.{ACL, AwsAccount, Permission}

import scala.util.{Failure, Try}

object Owners {
  def accountOwnerInformation(accounts: List[AwsAccount], access: ACL)(
      lookupConfiguredRole: AwsAccount => Try[String]
  ): List[(AwsAccount, List[(String, Set[Permission])], Try[String])] =
    accounts
      .sortBy(_.name.toLowerCase)
      .map { awsAccount =>
        (
          awsAccount,
          accountPermissions(awsAccount, access),
          lookupConfiguredRole(awsAccount)
        )
      }

  def accountPermissions(
      account: AwsAccount,
      acl: ACL
  ): List[(String, Set[Permission])] = {
    acl.userAccess
      .flatMap { case (username, permissions) =>
        if (permissions.exists(_.account == account))
          Some(username -> permissions.filter(_.account == account))
        else None
      }
      .toList
      .sortBy(_._1)
  }

  def accountIdErrors(accountData: Seq[(AwsAccount, List[(String, Set[Permission])], Try[String])]): Seq[(AwsAccount, Throwable)] = {
    accountData
      .collect { case (account, _, Failure(err)) =>
        (account, err)
      }
  }
}
