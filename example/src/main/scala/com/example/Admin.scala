package com.example

import com.example.Accounts.*
import com.example.Policies.AccountExtensions
import com.gu.janus.model.*

object Admin {

  /** These individuals have admin access to all accounts.
    */
  private val fullAdminUsers = Set[String](
    "sherlock.holmes"
  )
  private val fullAdminAccess = allAccounts.flatMap(_.accountAdmin)
  private val fullAdmin = fullAdminUsers
    .map(adminUser =>
      adminUser -> ACLEntry(permissions = fullAdminAccess, roles = Set.empty)
    )
    .toMap

  val acl = ACL(fullAdmin)
}
