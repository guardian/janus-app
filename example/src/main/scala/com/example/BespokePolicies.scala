package com.example

import com.example.Accounts.{Production, Security}
import com.example.Statements.{policy, s3ReadAccess}
import com.gu.janus.model.Permission


/**
 * These are policies that apply to specific resources in fixed accounts,
 * rather than general permission to be re-used everywhere.
 * Separated for clarity.
 */
object BespokePolicies {
  val securityCloudtrailLogs = Set(Permission(
    Security, "security-cloudtrail-logs", "Access to Security Cloudtrail logs",
    policy(s3ReadAccess("example-cloudtrail", "/"))
  ))

  val productionAccessLogs = Set(Permission(
    Production, "production-access-logs", "Read access to Production logs",
    policy(s3ReadAccess("example-access-logs", "/"))
  ))
}
