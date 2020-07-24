package com.example

import Accounts._
import Policies.AccountExtensions
import com.gu.janus.model._


object Admin {
  type Admins = Set[String]

  /**
    * These individuals have admin access to all accounts.
    */
  private val fullAdminUsers = Set[String](
    "sherlock.holmes"
  )
  private val fullAdminAccess = allAccounts.flatMap(_.accountAdmin)
  private val fullAdmin: Map[String, Set[Permission]] = fullAdminUsers.map(_ -> fullAdminAccess).toMap

  val acl = ACL(fullAdmin)
}