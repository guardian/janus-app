package com.gu.janus.transition.aws

import awscala.{Action, Policy, Resource, Statement}
import com.amazonaws.auth.policy

object AwScalaPolicy {

  def buildPolicy(statements: Seq[Statement]): Policy =
    Policy(statements)

  def buildStatement(
      effect: policy.Statement.Effect,
      actions: Seq[Action],
      resources: Seq[Resource]
  ): Statement =
    Statement(effect, actions, resources)

  def buildAction(name: String): Action =
    Action(name)

  def buildResource(name: String): Resource =
    Resource(name)
}
