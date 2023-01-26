package com.example

import awscala.{Action, Resource, Statement}
import com.amazonaws.auth.policy.Statement.Effect
import com.example.Accounts.{Production, Root, Security}
import com.gu.janus.model.Permission
import com.gu.janus.policy.Statements.{policy, s3ReadAccess}

/** These are policies that apply to specific resources in fixed accounts,
  * rather than general permission to be re-used everywhere. Separated for
  * clarity.
  */
object BespokePolicies {
  val securityCloudtrailLogs = Set(
    Permission(
      Security,
      "security-cloudtrail-logs",
      "Access to Security Cloudtrail logs",
      policy(s3ReadAccess("example-cloudtrail", "/"))
    )
  )

  val productionAccessLogs = Set(
    Permission(
      Production,
      "production-access-logs",
      "Read access to Production logs",
      policy(s3ReadAccess("example-access-logs", "/"))
    )
  )

  /** Service Control Polices (SCPs) are part of AWS Organisations, read about
    * the features here:
    * https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_policies_scps.html
    *
    * You can only manage policies within the account that administers your AWS
    * Organisation This policy will give a user the required permissions to
    * manage SCPs.
    */
  val serviceControlPolicyManagement = Set(
    Permission(
      Root,
      "service-control-policy",
      "Service Control Policy management",
      policy(
        Seq(
          Statement(
            Effect.Allow,
            Seq(
              Action("organizations:Describe*"),
              Action("organizations:List*"),
              Action("organizations:UpdatePolicy"),
              Action("organizations:DetachPolicy"),
              Action("organizations:EnablePolicyType"),
              Action("organizations:AttachPolicy"),
              Action("organizations:DeletePolicy"),
              Action("organizations:DisablePolicyType"),
              Action("organizations:CreatePolicy")
            ),
            Seq(Resource("*"))
          )
        )
      )
    )
  )
}
