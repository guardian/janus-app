package aws

import awscala.Region
import awscala.dynamodbv2.DynamoDB
import awscala.sts.STS
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{
  AWSCredentialsProviderChain,
  InstanceProfileCredentialsProvider
}

import scala.annotation.nowarn

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

  @nowarn("cat=deprecation")
  def localDb: DynamoDB = {
    val client =
      DynamoDB("fakeMyKeyId", "fakeSecretAccessKey")(Region.default())
    // this deprecated approach is required by the awscala helpers currently in use
    // we suppress this warning, above
    client.setEndpoint("http://localhost:8000")
    client
  }
}
