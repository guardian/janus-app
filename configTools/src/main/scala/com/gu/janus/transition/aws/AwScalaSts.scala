package com.gu.janus.transition.aws

import awscala.sts.STS
import com.amazonaws.auth.AWSCredentialsProvider

object AwScalaSts {

  def buildSts(credentialsProvider: AWSCredentialsProvider): STS =
    STS(credentialsProvider)
}
