package com.gu.janus.transition.aws

import awscala.dynamodbv2.DynamoDB
import com.amazonaws.regions.Region

object AwScalaDynamoDb {

  def buildDynamoDb(accessKeyId: String, secretAccessKey: String)(implicit
      region: Region
  ): DynamoDB =
    DynamoDB(accessKeyId, secretAccessKey)

  def buildDynamoDb(region: Region): DynamoDB =
    DynamoDB.at(region)
}
