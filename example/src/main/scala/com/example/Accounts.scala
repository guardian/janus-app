package com.example

import com.gu.janus.model.AwsAccount


object Accounts {
  // This is the account that administers your AWS Organisation
  val Root = AwsAccount("Root", "root")
  // Member account for Security auditing and monitoring in AWS, including:
  //  - centralised collection of security related logs including CloudTrail
  //  - delegated administrator for organisation-wide AWS GuardDuty
  val Security = AwsAccount("Security", "security")
  // The account our example company is going to use for production systems
  val Production = AwsAccount("Production", "production")
  // The account our example company is going to use for everything else
  val Staging = AwsAccount("Staging", "staging")


  val allAccounts: Set[AwsAccount] = Set(Root, Security, Production, Staging)

}
