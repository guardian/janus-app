package logic

import com.gu.janus.model.{ACL, AwsAccount, Permission, ACLEntry}

import scala.util.{Failure, Try}

object Owners {

  /** For now an "owner" is anyone that has any kind of access to an account.
    */
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
      .flatMap { case (username, ACLEntry(permissions, roles)) =>
        val allPermissions =
          permissions ++ roles.flatMap(_.permissions)
        if (allPermissions.exists(_.account == account))
          Some(username -> allPermissions.filter(_.account == account))
        else None
      }
      .toList
      .sortBy(_._1)
  }

  def accountIdErrors(
      accountData: Seq[
        (AwsAccount, List[(String, Set[Permission])], Try[String])
      ]
  ): Seq[(AwsAccount, Throwable)] = {
    accountData
      .collect { case (account, _, Failure(err)) =>
        (account, err)
      }
  }
}
