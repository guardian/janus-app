package fixtures

import com.gu.janus.model.{AwsAccount, Permission}
import com.gu.janus.policy.Iam.Policy

object Fixtures {
  val fooAct: AwsAccount = AwsAccount("Foo", "foo")
  val barAct: AwsAccount = AwsAccount("Bar", "bar")
  val bazAct: AwsAccount = AwsAccount("Baz", "baz")
  val quxAct: AwsAccount = AwsAccount("Qux", "qux")

  val fooDev: Permission = developerPermission(fooAct)
  val fooCf: Permission = accountAdminPermission(fooAct)
  val fooS3: Permission = s3ManagerPermission(fooAct)

  val barDev: Permission = developerPermission(barAct)
  val barCf: Permission = accountAdminPermission(barAct)

  val bazDev: Permission = developerPermission(bazAct)
  val bazCf: Permission = accountAdminPermission(bazAct)

  val quxDev: Permission = developerPermission(quxAct)
  val quxCf: Permission = accountAdminPermission(quxAct)

  val allTestPerms: Set[Permission] =
    Set(fooDev, fooCf, fooS3, barDev, barCf, bazDev, bazCf, quxDev, quxCf)

  // utilities (hard-coded conventions for now)
  def developerPermission(awsAccount: AwsAccount): Permission =
    Permission(awsAccount, "dev", "Developer", Policy(Seq.empty))
  def kinesisReadPermission(awsAccount: AwsAccount): Permission =
    Permission(awsAccount, "kinesis-read", "Kinesis Read", Policy(Seq.empty))
  def lambdaPermission(awsAccount: AwsAccount): Permission =
    Permission(awsAccount, "lambda", "AWS Lambda access", Policy(Seq.empty))
  def s3ReaderPermission(awsAccount: AwsAccount): Permission =
    Permission(awsAccount, "s3-read", "S3 Read", Policy(Seq.empty))
  def accountAdminPermission(awsAccount: AwsAccount): Permission =
    Permission(
      awsAccount,
      "cloudformation",
      "Account admin",
      Policy(Seq.empty),
      shortTerm = true
    )
  def s3ManagerPermission(awsAccount: AwsAccount): Permission =
    Permission(awsAccount, "s3-all", "S3 Read and Write", Policy(Seq.empty))
}
