package com.example

import Policies.AccountExtensions
import com.gu.janus.model._


object Access {

  import Accounts._

  // Note: default permissions are granted to every developer that is named below
  private val defaultPermissions = Production.support ++ Staging.support

  private val securityAccess = Security.all ++ allAccounts.flatMap(_.securityReview)

  private val users: List[(String, Set[Permission])] = List(
    "sherlock.holmes" -> (Root.dev ++ securityAccess),
    "john.watson" -> (Production.dev ++ Staging.dev ++ Root.billing),
    "irene.adler" -> (Production.dev ++ Staging.dev),
  )

  val acl = ACL(users.toMap, defaultPermissions)

}