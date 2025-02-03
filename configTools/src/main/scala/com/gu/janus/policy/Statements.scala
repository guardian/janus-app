package com.gu.janus.policy

import software.amazon.awssdk.policybuilder.iam.IamEffect.ALLOW
import software.amazon.awssdk.policybuilder.iam.{
  IamEffect,
  IamPolicy,
  IamStatement
}

import scala.jdk.CollectionConverters._

object Statements {

  def policy(statements: Seq[IamStatement]*): IamPolicy = {
    IamPolicy.builder().statements(statements.flatten.distinct.asJava).build()
  }

  private[policy] def enforceCorrectPath(path: String): Boolean = {
    if (path == "/") true
    else {
      path.headOption.contains('/') && !path.lastOption.contains('/')
    }
  }

  private[policy] def hierarchyPath(path: String) =
    s"${path.stripSuffix("/")}/*"

  /** Grants read-only access to a given path in an s3 bucket.
    *
    * Provided path should include leading slash and omit trailing slash.
    */
  def s3ReadAccess(
      bucketName: String,
      path: String,
      effect: IamEffect = ALLOW
  ): Seq[IamStatement] = {
    assert(
      enforceCorrectPath(path),
      s"Provided path should include leading slash and omit trailing slash ($bucketName :: $path)"
    )
    s3ConsoleEssentials(bucketName) :+
      IamStatement
        .builder()
        .effect(effect)
        .addAction("s3:Get*")
        .addAction("s3:List*")
        .addResource(s"arn:aws:s3:::$bucketName$path")
        .addResource(s"arn:aws:s3:::$bucketName${hierarchyPath(path)}")
        .build()
  }

  /** Grants full access to a given path in an s3 bucket.
    *
    * Provided path should include leading slash and omit trailing slash.
    */
  def s3FullAccess(bucketName: String, path: String): Seq[IamStatement] = {
    assert(
      enforceCorrectPath(path),
      s"Provided path should include leading slash and omit trailing slash ($bucketName :: $path)"
    )
    s3ConsoleEssentials(bucketName) :+
      IamStatement
        .builder()
        .effect(ALLOW)
        .addAction("s3:*")
        .addResource(s"arn:aws:s3:::$bucketName$path")
        .addResource(s"arn:aws:s3:::$bucketName${hierarchyPath(path)}")
        .build()
  }

  /** Grants permissions for basic s3 console access (list buckets and
    * locations)
    *
    * Typically bundled as part of other S3 permissions.
    */
  def s3ConsoleEssentials(bucketName: String): Seq[IamStatement] = {
    Seq(
      IamStatement
        .builder()
        .effect(ALLOW)
        .addAction("s3:ListAllMyBuckets")
        .addAction("s3:GetBucketLocation")
        .addResource("arn:aws:s3:::*")
        .build(),
      IamStatement
        .builder()
        .effect(ALLOW)
        .addAction("s3:ListBucket")
        .addResource(s"arn:aws:s3:::$bucketName")
        .build()
    )
  }

  /** Policy statements that allow basic access to s3 bucket (console view and
    * object list)
    */
  def s3BucketBasicAccessStatements(bucketName: String): Seq[IamStatement] = {
    Seq(
      IamStatement
        .builder()
        .effect(ALLOW)
        .addAction("s3:ListAllMyBuckets")
        .addAction("s3:GetBucketLocation")
        .addResource("arn:aws:s3:::*")
        .build(),
      IamStatement
        .builder()
        .effect(ALLOW)
        .addAction("s3:ListBucket")
        .addResource(s"arn:aws:s3:::$bucketName")
        .build()
    )
  }
}
