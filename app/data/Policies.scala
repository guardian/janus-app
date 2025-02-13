package data

import com.gu.janus.model.{AwsAccount, Permission}
import software.amazon.awssdk.policybuilder.iam.IamEffect.ALLOW
import software.amazon.awssdk.policybuilder.iam.{IamPolicy, IamStatement}

object Policies {
  private val revokeAccess = IamPolicy
    .builder()
    .addStatement(
      IamStatement
        .builder()
        .effect(ALLOW)
        .addAction("iam:PutRolePolicy")
        .addAction("iam:getRole")
        .addResource("*")
        .build()
    )
    .build()
  def revokeAccessPermission(awsAccount: AwsAccount): Permission =
    Permission(
      awsAccount,
      "revoke-access",
      "Revoke Janus access",
      revokeAccess,
      shortTerm = true
    )
}
