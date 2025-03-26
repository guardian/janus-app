package data

import com.gu.janus.model.{AwsAccount, Permission}
import com.gu.janus.policy.Iam._

object Policies {
  val revokeAccess = Policy(
    Seq(
      Statement(
        Effect.Allow,
        Seq(
          Action("iam:PutRolePolicy"),
          Action("iam:getRole")
        ),
        Seq(Resource("*"))
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

  /**
   * A policy that grants no permissions.
   *
   * This is used as the inline policy
   */
  val noOp = Policy(
    Seq(
      Statement(
        Effect.Allow,
        Seq(
          Action("sts:GetCallerIdentity")
        ),
        Seq(Resource("*"))
      )
    )
  )
}
