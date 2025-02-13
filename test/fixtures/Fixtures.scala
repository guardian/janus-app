package fixtures

import com.gu.janus.model.{AwsAccount, Permission}
import software.amazon.awssdk.policybuilder.iam.IamEffect.ALLOW
import software.amazon.awssdk.policybuilder.iam.{IamPolicy, IamStatement}

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
    Permission(
      awsAccount,
      "dev",
      "Developer",
      IamPolicy
        .builder()
        .addStatement(IamStatement.builder().effect(ALLOW).build())
        .build()
    )
  def kinesisReadPermission(awsAccount: AwsAccount) =
    Permission(
      awsAccount,
      "kinesis-read",
      "Kinesis Read",
      IamPolicy
        .builder()
        .addStatement(IamStatement.builder().effect(ALLOW).build())
        .build()
    )
  def lambdaPermission(awsAccount: AwsAccount) =
    Permission(
      awsAccount,
      "lambda",
      "AWS Lambda access",
      IamPolicy
        .builder()
        .addStatement(IamStatement.builder().effect(ALLOW).build())
        .build()
    )
  def s3ReaderPermission(awsAccount: AwsAccount) =
    Permission(
      awsAccount,
      "s3-read",
      "S3 Read",
      IamPolicy
        .builder()
        .addStatement(IamStatement.builder().effect(ALLOW).build())
        .build()
    )
  def accountAdminPermission(awsAccount: AwsAccount) =
    Permission(
      awsAccount,
      "cloudformation",
      "Account admin",
      IamPolicy
        .builder()
        .addStatement(IamStatement.builder().effect(ALLOW).build())
        .build(),
      shortTerm = true
    )
  def s3ManagerPermission(awsAccount: AwsAccount) =
    Permission(
      awsAccount,
      "s3-all",
      "S3 Read and Write",
      IamPolicy
        .builder()
        .addStatement(IamStatement.builder().effect(ALLOW).build())
        .build()
    )
}
