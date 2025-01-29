package data

import awscala.Effect
import com.gu.janus.model.{AwsAccount, Permission}
import com.gu.janus.transition.aws.AwScalaPolicy

object Policies {
  val revokeAccess = AwScalaPolicy.buildPolicy(
    Seq(
      AwScalaPolicy.buildStatement(
        Effect.Allow,
        Seq(
          AwScalaPolicy.buildAction("iam:PutRolePolicy"),
          AwScalaPolicy.buildAction("iam:getRole")
        ),
        Seq(AwScalaPolicy.buildResource("*"))
      )
    )
  )
  def revokeAccessPermission(awsAccount: AwsAccount) =
    Permission(
      awsAccount,
      "revoke-access",
      "Revoke Janus access",
      revokeAccess,
      true
    )
}
