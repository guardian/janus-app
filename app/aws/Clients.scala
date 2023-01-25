package aws

import awscala.sts.STS
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{
  AWSCredentialsProviderChain,
  InstanceProfileCredentialsProvider
}

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
}
