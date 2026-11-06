package com.example

import com.example.Accounts.*
import com.example.Policies.AccountExtensions
import com.gu.janus.model.*

object Superuser {

  /** These individuals have superuser access to all accounts.
    */
  private val superusers = Set[String](
    "sherlock.holmes"
  )
  private val superuserAccess = allAccounts.flatMap(_.accountAdmin)
  private val superuserAcl = superusers
    .map(superuser =>
      superuser -> ACLEntry(
        permissions = superuserAccess,
        policyGrants = Set.empty
      )
    )
    .toMap

  val acl = ACL(superuserAcl)
}
