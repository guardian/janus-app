package com.gu.janus.policy


import awscala._
import com.amazonaws.auth.policy.Statement.Effect


object Statements {

  def policy(statements: Seq[Statement]*): Policy = {
    Policy(statements.flatten.distinct)
  }

  private[policy] def enforceCorrectPath(path: String): Boolean = {
    if (path == "/") true
    else {
      path.headOption.contains('/') && !path.lastOption.contains('/')
    }
  }

  private[policy] def hierarchyPath(path: String) = s"${path.stripSuffix("/")}/*"

  /**
    * Grants read-only access to a given path in an s3 bucket.
    *
    * Provided path should include leading slash and omit trailing slash.
    */
  def s3ReadAccess(bucketName: String, path: String, effect: Effect = Effect.Allow): Seq[Statement] = {
    assert(enforceCorrectPath(path), s"Provided path should include leading slash and omit trailing slash ($bucketName :: $path)")
    s3ConsoleEssentials(bucketName) :+
      Statement(
        effect,
        Seq(Action("s3:Get*"), Action("s3:List*")),
        Seq(
          Resource(s"arn:aws:s3:::$bucketName$path"),
          Resource(s"arn:aws:s3:::$bucketName${hierarchyPath(path)}")
        )
      )
  }

  /**
    * Grants full access to a given path in an s3 bucket.
    *
    * Provided path should include leading slash and omit trailing slash.
    */
  def s3FullAccess(bucketName: String, path: String): Seq[Statement] = {
    assert(enforceCorrectPath(path), s"Provided path should include leading slash and omit trailing slash ($bucketName :: $path)")
    s3ConsoleEssentials(bucketName) :+
      Statement(
        Effect.Allow,
        Seq(Action("s3:*")),
        Seq(
          Resource(s"arn:aws:s3:::$bucketName$path"),
          Resource(s"arn:aws:s3:::$bucketName${hierarchyPath(path)}")
        )
      )
  }

  /**
    * Grants permissions for basic s3 console access (list buckets and locations)
    *
    * Typically bundled as part of other S3 permissions.
    */
  def s3ConsoleEssentials(bucketName: String): Seq[Statement] = {
    Seq(
      Statement(Effect.Allow,
        Seq(
          Action("s3:ListAllMyBuckets"),
          Action("s3:GetBucketLocation")
        ),
        Seq(Resource("arn:aws:s3:::*"))
      ),
      Statement(Effect.Allow,
        Seq(Action("s3:ListBucket")),
        Seq(Resource(s"arn:aws:s3:::$bucketName"))
      )
    )
  }

  /**
    * Policy statements that allow basic access to s3 bucket (console view and object list)
    */
  def s3BucketBasicAccessStatements(bucketName: String): Seq[Statement] = {
    Seq(
      Statement(Effect.Allow,
        Seq(
          Action("s3:ListAllMyBuckets"),
          Action("s3:GetBucketLocation")
        ),
        Seq(Resource("arn:aws:s3:::*"))
      ),
      Statement(Effect.Allow,
        Seq(Action("s3:ListBucket")),
        Seq(Resource(s"arn:aws:s3:::$bucketName"))
      )
    )
  }
}
