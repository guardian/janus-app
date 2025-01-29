package com.gu.janus.policy

import awscala.{Policy, Statement}
import com.amazonaws.auth.policy.Statement.Effect
import com.gu.janus.transition.aws.AwScalaPolicy

object Statements {

  def policy(statements: Seq[Statement]*): Policy = {
    AwScalaPolicy.buildPolicy(statements.flatten.distinct)
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
      effect: Effect = Effect.Allow
  ): Seq[Statement] = {
    assert(
      enforceCorrectPath(path),
      s"Provided path should include leading slash and omit trailing slash ($bucketName :: $path)"
    )
    s3ConsoleEssentials(bucketName) :+
      AwScalaPolicy.buildStatement(
        effect,
        Seq(AwScalaPolicy.buildAction("s3:Get*"), AwScalaPolicy.buildAction("s3:List*")),
        Seq(
          AwScalaPolicy.buildResource(s"arn:aws:s3:::$bucketName$path"),
          AwScalaPolicy.buildResource(s"arn:aws:s3:::$bucketName${hierarchyPath(path)}")
        )
      )
  }

  /** Grants full access to a given path in an s3 bucket.
    *
    * Provided path should include leading slash and omit trailing slash.
    */
  def s3FullAccess(bucketName: String, path: String): Seq[Statement] = {
    assert(
      enforceCorrectPath(path),
      s"Provided path should include leading slash and omit trailing slash ($bucketName :: $path)"
    )
    s3ConsoleEssentials(bucketName) :+
      AwScalaPolicy.buildStatement(
        Effect.Allow,
        Seq(AwScalaPolicy.buildAction("s3:*")),
        Seq(
          AwScalaPolicy.buildResource(s"arn:aws:s3:::$bucketName$path"),
          AwScalaPolicy.buildResource(s"arn:aws:s3:::$bucketName${hierarchyPath(path)}")
        )
      )
  }

  /** Grants permissions for basic s3 console access (list buckets and
    * locations)
    *
    * Typically bundled as part of other S3 permissions.
    */
  def s3ConsoleEssentials(bucketName: String): Seq[Statement] = {
    Seq(
      AwScalaPolicy.buildStatement(
        Effect.Allow,
        Seq(
          AwScalaPolicy.buildAction("s3:ListAllMyBuckets"),
          AwScalaPolicy.buildAction("s3:GetBucketLocation")
        ),
        Seq(AwScalaPolicy.buildResource("arn:aws:s3:::*"))
      ),
      AwScalaPolicy.buildStatement(
        Effect.Allow,
        Seq(AwScalaPolicy.buildAction("s3:ListBucket")),
        Seq(AwScalaPolicy.buildResource(s"arn:aws:s3:::$bucketName"))
      )
    )
  }

  /** Policy statements that allow basic access to s3 bucket (console view and
    * object list)
    */
  def s3BucketBasicAccessStatements(bucketName: String): Seq[Statement] = {
    Seq(
      AwScalaPolicy.buildStatement(
        Effect.Allow,
        Seq(
          AwScalaPolicy.buildAction("s3:ListAllMyBuckets"),
          AwScalaPolicy.buildAction("s3:GetBucketLocation")
        ),
        Seq(AwScalaPolicy.buildResource("arn:aws:s3:::*"))
      ),
      AwScalaPolicy.buildStatement(
        Effect.Allow,
        Seq(AwScalaPolicy.buildAction("s3:ListBucket")),
        Seq(AwScalaPolicy.buildResource(s"arn:aws:s3:::$bucketName"))
      )
    )
  }
}
