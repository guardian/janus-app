package com.example

import awscala.{Action, Resource, Statement}
import com.amazonaws.auth.policy.Statement.Effect
import com.example.Accounts.{Production, Root, Security}
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

  // Service control policies (SCPs) can use to manage permissions in your organization.
  // Attaching an SCP to an organization, organization unit (OU) or account will define
  // a guardrail (sets limits) on the actions that can be performed within the account.
  val serviceControlPolicy = Set(
    Permission(Root, "service-control-policy", "Service Control Policy management",
      policy(
        Seq(
          Statement(Effect.Allow,
            Seq(
              Action("organizations:Describe*"),
              Action("organizations:List*"),
              Action("organizations:UpdatePolicy"),
              Action("organizations:DetachPolicy"),
              Action("organizations:EnablePolicyType"),
              Action("organizations:AttachPolicy"),
              Action("organizations:DeletePolicy"),
              Action("organizations:DisablePolicyType"),
              Action("organizations:CreatePolicy"),
            ),
            Seq(Resource("*"))
          )
        )
      ))
  )
}
