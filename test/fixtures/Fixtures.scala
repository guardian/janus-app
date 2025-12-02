package fixtures

import com.gu.janus.model.{AwsAccount, Permission, ProvisionedRole}
import com.gu.janus.policy.Iam.Policy

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

  val allTestPerms: Set[Permission] =
    Set(fooDev, fooCf, fooS3, barDev, barCf, bazDev, bazCf, quxDev, quxCf)
  val allTestPerms2: Set[Permission | ProvisionedRole] =
    allTestPerms.map(p => p: Permission | ProvisionedRole)

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
}
