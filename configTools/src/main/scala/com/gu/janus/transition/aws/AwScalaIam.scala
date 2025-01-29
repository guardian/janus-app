package com.gu.janus.transition.aws

import awscala.iam.{IAM, Role, RolePolicy}
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.identitymanagement

object AwScalaIam {

  def buildIam(credentialsProvider: AWSCredentialsProvider): IAM =
    IAM(credentialsProvider)

  def buildRole(role: identitymanagement.model.Role): Role =
    Role(role)

  def buildRolePolicy(
      role: Role,
      name: String,
      document: String
  ): RolePolicy =
    RolePolicy(role, name, document)
}
