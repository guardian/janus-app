package aws

import awscala.sts.STS
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{
  AWSCredentialsProviderChain,
  InstanceProfileCredentialsProvider
}
import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  StaticCredentialsProvider
}
import software.amazon.awssdk.regions.Region.EU_WEST_1
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import java.net.URI

object Clients {
  // local dev is in a separate profile name so it isn't overwritten when you obtain credentials using Janus
  // this profile needs credentials sufficient to power Janus (specifically assumeRole), which is probably
  // different to the credentials you normally want to use
  val profileName = "janus"

  lazy val credentialsProviderChain: AWSCredentialsProviderChain = {
    new AWSCredentialsProviderChain(
      InstanceProfileCredentialsProvider.getInstance(),
      new ProfileCredentialsProvider(profileName)
    )
  }

  lazy val stsClient: STS = {
    STS(credentialsProviderChain)
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
