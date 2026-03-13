package fixtures

import com.gu.janus.model.{AwsAccount, DeveloperPolicyGrant, Permission}
import com.gu.janus.policy.Iam.Policy
import models.DeveloperPolicy

object Fixtures {
  val fooAccount = AwsAccount("Foo", "foo")
  val barAct = AwsAccount("Bar", "bar")
  val bazAct = AwsAccount("Baz", "baz")
  val quxAct = AwsAccount("Qux", "qux")

  val fooDev = developerPermission(fooAccount)
  val fooCf = accountAdminPermission(fooAccount)
  val fooS3 = s3ManagerPermission(fooAccount)

  val barDev = developerPermission(barAct)
  val barCf = accountAdminPermission(barAct)

  val bazDev = developerPermission(bazAct)
  val bazCf = accountAdminPermission(bazAct)

  val quxDev = developerPermission(quxAct)
  val quxCf = accountAdminPermission(quxAct)

  val allTestPerms =
    Set(fooDev, fooCf, fooS3, barDev, barCf, bazDev, bazCf, quxDev, quxCf)

  // utilities (hard-coded conventions for now)
  def developerPermission(awsAccount: AwsAccount) =
    Permission(awsAccount, "dev", "Developer", Policy(Seq.empty))
  def kinesisReadPermission(awsAccount: AwsAccount) =
    Permission(awsAccount, "kinesis-read", "Kinesis Read", Policy(Seq.empty))
  def lambdaPermission(awsAccount: AwsAccount) =
    Permission(awsAccount, "lambda", "AWS Lambda access", Policy(Seq.empty))
  def s3ReaderPermission(awsAccount: AwsAccount) =
    Permission(awsAccount, "s3-read", "S3 Read", Policy(Seq.empty))
  def accountAdminPermission(awsAccount: AwsAccount) =
    Permission(
      awsAccount,
      "cloudformation",
      "Account admin",
      Policy(Seq.empty),
      shortTerm = true
    )
  def s3ManagerPermission(awsAccount: AwsAccount) =
    Permission(awsAccount, "s3-all", "S3 Read and Write", Policy(Seq.empty))

  val grantAlpha = DeveloperPolicyGrant(name = "alpha", id = "alpha-id")
  val grantBeta = DeveloperPolicyGrant(name = "beta", id = "beta-id")
  val grantGamma = DeveloperPolicyGrant(name = "gamma", id = "gamma-id")
  val grantDelta = DeveloperPolicyGrant(name = "delta", id = "delta-id")

  val developerPolicyAlphaFoo1 = DeveloperPolicy(
    policyArnString = "arn:aws:iam::123456789012:policy/alpha1",
    policyName = "alpha-1",
    policyGrantId = grantAlpha.id,
    description = Some("Alpha policy"),
    account = fooAccount
  )
  val developerPolicyAlphaFoo2 = DeveloperPolicy(
    policyArnString = "arn:aws:iam::123456789012:policy/alpha2",
    policyName = "alpha-2",
    policyGrantId = grantAlpha.id,
    description = Some("Alpha policy"),
    account = fooAccount
  )
  val developerPolicyAlphaBar = DeveloperPolicy(
    policyArnString = "arn:aws:iam::123456789012:policy/alpha",
    policyName = "alpha",
    policyGrantId = grantAlpha.id,
    description = Some("Alpha policy"),
    account = barAct
  )
  val developerPolicyBetaFoo = DeveloperPolicy(
    policyArnString = "arn:aws:iam::123456789012:policy/beta",
    policyName = "beta",
    policyGrantId = grantBeta.id,
    description = Some("Beta policy"),
    account = fooAccount
  )
  val developerPolicyBetaBar = DeveloperPolicy(
    policyArnString = "arn:aws:iam::123456789012:policy/beta",
    policyName = "beta",
    policyGrantId = grantBeta.id,
    description = Some("Beta policy"),
    account = barAct
  )
  val developerPolicyGammaBaz = DeveloperPolicy(
    policyArnString = "arn:aws:iam::123456789012:policy/gamma",
    policyName = "gamma",
    policyGrantId = grantGamma.id,
    description = Some("Gamma policy"),
    account = bazAct
  )
  val developerPolicyDeltaQux = DeveloperPolicy(
    policyArnString = "arn:aws:iam::123456789012:policy/delta",
    policyName = "delta",
    policyGrantId = grantDelta.id,
    description = Some("Delta policy"),
    account = quxAct
  )
}
