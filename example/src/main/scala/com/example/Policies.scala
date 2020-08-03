package com.example

import awscala._
import com.gu.janus.model.{AwsAccount, Permission}


object Policies {
  /**
    * Access to most AWS functionality. Excludes being able to create credentials to bypass Janus.
    */
  val developer = Policy(Seq(
    Statement(Effect.Allow, Seq(Action("*")), Seq(Resource("*"))),
    Statement(
      Effect.Deny,
      Seq(
        // role assumption from this role should not be allowed
        Action("sts:assumeRole*"),
        // Disallow creating IAM user access keys
        Action("iam:CreateAccessKey"),
        Action("iam:UpdateAccessKey"),
        // Disallow IAM user password management
        Action("iam:CreateLoginProfile"),
        Action("iam:UpdateLoginProfile"),
        Action("iam:ChangePassword"),
        // Disallow IAM user MFA management
        Action("iam:CreateVirtualMFADevice"),
        Action("iam:EnableMFADevice"),
        Action("iam:DeactivateMFADevice"),
        Action("iam:DeleteVirtualMFADevice"),
        Action("iam:ResyncMFADevice")
      ),
      Seq(Resource("*"))
    )
  ))

  def developerPermission(awsAccount: AwsAccount) =
    Permission(awsAccount, "dev", "Developer", developer)

  /**
    * This policy is incredibly permissive and should be used sparingly.
    * Where it really must be used, limit its scope by making it a short-term permission.
    */
  val accountAdmin = Policy(Seq(
    Statement(
      Effect.Allow,
      Seq(Action("*")),
      Seq(Resource("*")))
  ))

  def accountAdminPermission(awsAccount: AwsAccount) =
    Permission(awsAccount, "admin", "Account admin", accountAdmin, shortTerm = true)

  /**
    * Grants access to s3. Can be useful for data analysts and others that do not require dev.
    */
  val s3Manager = Policy(Seq(
    Statement(Effect.Allow, Seq(Action("s3:*")), Seq(Resource("*")))
  ))

  val s3Reader = Policy(Seq(
    Statement(Effect.Allow, Seq(
      Action("s3:GetBucketLocation"),
      Action("s3:GetObject"),
      Action("s3:GetObjectAcl"),
      Action("s3:GetObjectVersion"),
      Action("s3:GetObjectVersionAcl"),
      Action("s3:ListAllMyBuckets"),
      Action("s3:ListBucket"),
      Action("s3:ListBucketVersions")
    ), Seq(Resource("*")))
  ))

  def s3ManagerPermission(awsAccount: AwsAccount) =
    Permission(awsAccount, "s3-all", "S3 Read and Write", s3Manager)

  def s3ReaderPermission(awsAccount: AwsAccount) =
    Permission(awsAccount, "s3-read", "S3 Read", s3Reader)

  /**
    * Grants access to view billing information
    */
  val billing = Policy(Seq(
    Statement(Effect.Allow, Seq(
      Action("aws-portal:*"),
      Action("ce:*"),
      Action("cur:*"),
      Action("pricing:*"),
      Action("budgets:*")
    ), Seq(Resource("*")))
  ))

  def billingPermission(awsAccount: AwsAccount) =
    Permission(awsAccount, "billing", "Billing", billing)

  /**
    * Access to GuardDuty and Read only access to Trusted Advisor.
    */
  val securityReview = Policy(Seq(
    Statement(Effect.Allow, Seq(
      Action("trustedadvisor:Describe*"),
      Action("guardduty:*")
    ), Seq(Resource("*")))
  ))
  def securityReviewPermission(awsAccount: AwsAccount) =
    Permission(awsAccount, "security-review", "Security Review", securityReview)

  implicit class AccountExtensions(val account: AwsAccount) extends AnyVal {
    def dev = Set(developerPermission(account))
    def accountAdmin = Set(accountAdminPermission(account))

    // not included in all as these are subsets of dev
    def s3 = Set(s3ManagerPermission(account))
    def s3Read = Set(s3ReaderPermission(account))
    def billing = Set(billingPermission(account))
    def securityReview = Set(securityReviewPermission(account))
  }
}
