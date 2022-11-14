package com.example

import com.example.BespokePolicies._
import com.example.Policies.AccountExtensions
import com.gu.janus.model._


object Access {

  import Accounts._

  // It is possible to combine permissions using Scala
  private val securityAccess = Security.accountAdmin ++ Security.dev ++ allAccounts.flatMap(_.securityReview)

  val users: List[(String, Set[Permission])] = List(
    "sherlock.holmes" -> (Root.dev ++ securityAccess),
    "john.watson" -> (Production.dev ++ Staging.dev ++ Root.billing),
    "irene.adler" -> (Production.dev ++ Staging.dev ++ DataLake.dev),
    "mycroft.holmes" -> (productionAccessLogs ++ securityCloudtrailLogs)
  )

  // Default permissions are granted to every developer that is named below
  private val defaultPermissions = Production.billing ++ Staging.billing

  val acl = ACL(users.toMap, defaultPermissions)

}
