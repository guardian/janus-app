package logic

import com.gu.janus.model.{ACL, AccountOwners, AwsAccount}

object Owners {
  def accountOwners(account: AwsAccount, acl: ACL): AccountOwners = {
    val permissions = accountPermissions(account, acl)

    // hard-coded admin/dev conventions for now, TODO: improve accounts page
    val admins = permissions
      .filter { case (_, userPerms) =>
        userPerms.exists(_.label == "cloudformation")
      }
      .keys
      .toList
      .sorted
    val devs = permissions
      .filter { case (username, userPerms) =>
        userPerms.exists(_.label == "dev") && !admins.contains(username)
      }
      .keys
      .toList
      .sorted
    val others = permissions
      .filter { case (username, _) =>
        !admins.contains(username) && !devs.contains(username)
      }
      .keys
      .toList
      .sorted

    AccountOwners(admins, devs, others)
  }

  def accountPermissions(account: AwsAccount, acl: ACL) = {
    acl.userAccess
      .flatMap { case (username, permissions) =>
        if (permissions.exists(_.account == account))
          Some(username -> permissions.filter(_.account == account))
        else None
      }
  }
}
