package aws

import com.gu.janus.model.AwsAccount
import conf.Config
import play.api.Configuration
import software.amazon.awssdk.auth.credentials.*
import software.amazon.awssdk.regions.Region.EU_WEST_1
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
import software.amazon.awssdk.services.sts.model.PolicyDescriptorType

import java.net.URI

object Clients {

  /** This profile is specifically to give access to the assumeRole permission
    * in the Dev stage
    */
  private val janusProfileName = "janus"

  /** In the Dev stage, the security profile is used to access the Play secret
    * stored in parameter store
    */
  private val securityProfileName = "security"

  /** In production, we use the EC2 instance profile to access resources. And in
    * Dev, we use the given profile.
    */
  private def makeCredentialsProviderChain(
      profileName: String
  ): AwsCredentialsProviderChain =
    AwsCredentialsProviderChain
      .builder()
      .addCredentialsProvider(InstanceProfileCredentialsProvider.create())
      .addCredentialsProvider(ProfileCredentialsProvider.create(profileName))
      .build()

  private lazy val janusCredentialsProviderChain = makeCredentialsProviderChain(
    janusProfileName
  )

  private lazy val securityCredentialsProviderChain =
    makeCredentialsProviderChain(securityProfileName)

  lazy val stsClient: StsClient =
    StsClient
      .builder()
      .credentialsProvider(janusCredentialsProviderChain)
      .region(EU_WEST_1)
      .build()

  lazy val ssm: SsmClient = SsmClient
    .builder()
    .credentialsProvider(securityCredentialsProviderChain)
    .region(EU_WEST_1)
    .build()

  /** Gives an IAM client scoped to the given account so that it can look for
    * [[com.gu.janus.model.ProvisionedRole]] data.
    *
    * The client won't be able to make any calls unless the Janus user is
    * included in the trust relationship of the Janus role being assumed in the
    * target account. if not, building the client will succeed but it will be
    * unable to do anything. This means that in the dev environment special
    * configuration will be needed, which is described elsewhere.
    */
  def provisionedRoleReadingIam(
      account: AwsAccount,
      sts: StsClient,
      config: Configuration,
      roleSessionNamePrefix: String
  ): IamClient = {
    val roleArn = Config.roleArn(account.authConfigKey, config)
    val roleSessionName = s"$roleSessionNamePrefix-${account.authConfigKey}"

    // Auto-refreshes creds used by IAM client
    val credentialsProvider = StsAssumeRoleCredentialsProvider.builder
      .stsClient(sts)
      .asyncCredentialUpdateEnabled(true)
      .refreshRequest(builder =>
        builder
          .roleArn(roleArn)
          .roleSessionName(roleSessionName)
          .policyArns(
            PolicyDescriptorType.builder
              .arn("arn:aws:iam::aws:policy/IAMReadOnlyAccess")
              .build()
          )
          // 15 minutes, which is minimum AWS allows
          .durationSeconds(900)
          .build()
      )
      .build()

    IamClient.builder
      .credentialsProvider(credentialsProvider)
      // Builder seems to need a region even though IAM is global
      .region(EU_WEST_1)
      .build()
  }

  def localDb: DynamoDbClient =
    DynamoDbClient
      .builder()
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create("fakeMyKeyId", "fakeSecretAccessKey")
        )
      )
      .region(EU_WEST_1)
      .endpointOverride(URI.create("http://localhost:8000"))
      .build()
}
