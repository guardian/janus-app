package aws

import software.amazon.awssdk.auth.credentials._
import software.amazon.awssdk.regions.Region.EU_WEST_1
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.sts.StsClient

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
