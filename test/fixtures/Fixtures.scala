package fixtures

import com.gu.janus.model.{AwsAccount, DeveloperPolicyGrant, Permission}
import com.gu.janus.policy.Iam.Policy
import models.DeveloperPolicy

object Fixtures {
  val fooAct = AwsAccount("Foo", "foo")
  val barAct = AwsAccount("Bar", "bar")
  val bazAct = AwsAccount("Baz", "baz")
  val quxAct = AwsAccount("Qux", "qux")

  val fooDev = developerPermission(fooAct)
  val fooCf = accountAdminPermission(fooAct)
  val fooS3 = s3ManagerPermission(fooAct)

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

  val grantAlpha =
    DeveloperPolicyGrant(name = "alpha", id = "alpha-id", shortTerm = false)
  val grantBeta =
    DeveloperPolicyGrant(name = "beta", id = "beta-id", shortTerm = false)
  val grantGamma =
    DeveloperPolicyGrant(name = "gamma", id = "gamma-id", shortTerm = false)
  val grantDelta =
    DeveloperPolicyGrant(name = "delta", id = "delta-id", shortTerm = false)

  val developerPolicyAlphaFoo1 = DeveloperPolicy(
    policyArnString =
      "arn:aws:iam::123456789012:policy/guardian/test-repo/test-stack/PROD/alpha-id/alpha-1",
    policyName = "alpha-1",
    policyGrantId = grantAlpha.id,
    sourceRepo = "guardian/test-repo",
    stack = "test-stack",
    stage = "PROD",
    friendlyName = "Alpha policy",
    account = fooAct
  )
  val developerPolicyAlphaFoo2 = DeveloperPolicy(
    policyArnString =
      "arn:aws:iam::123456789012:policy/guardian/test-repo/test-stack/PROD/alpha-id/alpha-2",
    policyName = "alpha-2",
    policyGrantId = grantAlpha.id,
    sourceRepo = "guardian/test-repo",
    stack = "test-stack",
    stage = "PROD",
    friendlyName = "Alpha policy",
    account = fooAct
  )
  val developerPolicyAlphaBar = DeveloperPolicy(
    policyArnString =
      "arn:aws:iam::123456789012:policy/guardian/test-repo/test-stack/PROD/alpha-id/alpha",
    policyName = "alpha",
    policyGrantId = grantAlpha.id,
    sourceRepo = "guardian/test-repo",
    stack = "test-stack",
    stage = "PROD",
    friendlyName = "Alpha policy",
    account = barAct
  )
  val developerPolicyBetaFoo = DeveloperPolicy(
    policyArnString =
      "arn:aws:iam::123456789012:policy/guardian/test-repo/test-stack/PROD/beta-id/beta",
    policyName = "beta",
    policyGrantId = grantBeta.id,
    sourceRepo = "guardian/test-repo",
    stack = "test-stack",
    stage = "PROD",
    friendlyName = "Beta policy",
    account = fooAct
  )
  val developerPolicyBetaBar = DeveloperPolicy(
    policyArnString =
      "arn:aws:iam::123456789012:policy/guardian/test-repo/test-stack/PROD/beta-id/beta",
    policyName = "beta",
    policyGrantId = grantBeta.id,
    sourceRepo = "guardian/janus-app",
    stack = "test-stack",
    stage = "PROD",
    friendlyName = "Beta policy",
    account = barAct
  )
  val developerPolicyGammaBaz = DeveloperPolicy(
    policyArnString =
      "arn:aws:iam::123456789012:policy/guardian/test-repo/test-stack/PROD/gamma-id/gamma",
    policyName = "gamma",
    policyGrantId = grantGamma.id,
    sourceRepo = "guardian/janus-app",
    stack = "test-stack",
    stage = "PROD",
    friendlyName = "Gamma policy",
    account = bazAct
  )
  val developerPolicyDeltaQux = DeveloperPolicy(
    policyArnString =
      "arn:aws:iam::123456789012:policy/guardian/test-repo/test-stack/PROD/delta-id/delta",
    policyName = "delta",
    policyGrantId = grantDelta.id,
    sourceRepo = "guardian/janus-app",
    stack = "test-stack",
    stage = "PROD",
    friendlyName = "Delta policy",
    account = quxAct
  )
}
