package aws

import software.amazon.awssdk.auth.credentials._
import software.amazon.awssdk.http.apache.{ApacheHttpClient, ProxyConfiguration}
import software.amazon.awssdk.regions.Region.EU_WEST_1
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.sts.StsClient

import java.net.URI
import scala.jdk.CollectionConverters._

object Clients {
  // local dev is in a separate profile name so it isn't overwritten when you obtain credentials using Janus
  // this profile needs credentials sufficient to power Janus (specifically assumeRole), which is probably
  // different to the credentials you normally want to use
  val profileName = "janus"

  val proxyConfig =
    ProxyConfiguration.builder().nonProxyHosts(Set("*").asJava).build()
  val httpClient =
    ApacheHttpClient.builder().proxyConfiguration(proxyConfig).build()

  private lazy val credentialsProviderChain: AwsCredentialsProviderChain =
    AwsCredentialsProviderChain
      .builder()
      .addCredentialsProvider(InstanceProfileCredentialsProvider.create())
      .addCredentialsProvider(ProfileCredentialsProvider.create(profileName))
      .build()

  lazy val stsClient: StsClient =
    StsClient
      .builder()
      .httpClient(httpClient)
      .credentialsProvider(credentialsProviderChain)
      .region(EU_WEST_1)
      .build()

  def localDb: DynamoDbClient =
    DynamoDbClient
      .builder()
      .httpClient(httpClient)
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create("fakeMyKeyId", "fakeSecretAccessKey")
        )
      )
      .region(EU_WEST_1)
      .endpointOverride(URI.create("http://localhost:8000"))
      .build()
}
