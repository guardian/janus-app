package logic

import com.gu.janus.model.{ACL, AwsAccount, Permission}

object Owners {
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
}
