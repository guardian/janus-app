package logic

import com.gu.janus.model.{AwsAccount, DeveloperPolicyGrant}
import logic.DeveloperPolicies.{
  DEVELOPER_POLICY_NAMESPACE_PREFIX,
  developerPolicyDisplayName,
  developerPolicySlug,
  toDeveloperPolicy,
  toPermission
}
import models.*
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import software.amazon.awssdk.services.iam.model.Policy

import java.time.Instant

class DeveloperPoliciesTest
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaCheckPropertyChecks {

  private val account =
    AwsAccount(name = "Account Name", authConfigKey = "accId")

  private val policy: Policy =
    Policy
      .builder()
      .arn(
        "arn:aws:iam::123:policy/developer-policy/guardian/janus-app/my-stack/PROD/dev-pol-id/p1"
      )
      .path("/developer-policy/guardian/janus-app/my-stack/PROD/dev-pol-id/")
      .policyName("p1")
      .description("Description")
      .build()

  "toDeveloperPolicy" - {
    "should return a DeveloperPolicy when arguments are good" in {
      val result = toDeveloperPolicy(account, policy)

      result.value shouldBe DeveloperPolicy(
        policyArnString =
          "arn:aws:iam::123:policy/developer-policy/guardian/janus-app/my-stack/PROD/dev-pol-id/p1",
        policyName = "p1",
        policyGrantId = "dev-pol-id",
        sourceRepo = "guardian/janus-app",
        stack = "my-stack",
        stage = "PROD",
        friendlyName = "Description",
        account
      )
    }

    "should return None when the path has fewer than 6 sections" in {
      val policy = Policy
        .builder()
        .arn("arn:aws:iam::123:policy/developer-policy/guardian/janus-app/p1")
        .path("/developer-policy/guardian/janus-app/")
        .policyName("p1")
        .build()

      toDeveloperPolicy(account, policy) shouldBe None
    }

    "should return None when the path has more than 6 sections" in {
      val policy = Policy
        .builder()
        .arn(
          "arn:aws:iam::123:policy/developer-policy/guardian/janus-app/my-stack/PROD/dev-pol-id/extra/p1"
        )
        .path(
          "/developer-policy/guardian/janus-app/my-stack/PROD/dev-pol-id/extra/"
        )
        .policyName("p1")
        .build()

      toDeveloperPolicy(account, policy) shouldBe None
    }

    "should return None when the grant ID segment is empty" in {
      val policy = Policy
        .builder()
        .arn(
          "arn:aws:iam::123:policy/developer-policy/guardian/janus-app/my-stack/PROD//p1"
        )
        .path("/developer-policy/guardian/janus-app/my-stack/PROD//")
        .policyName("p1")
        .build()

      toDeveloperPolicy(account, policy) shouldBe None
    }

    "should return None when description is empty" in {
      val policy = Policy
        .builder()
        .arn(
          "arn:aws:iam::123:policy/developer-policy/guardian/janus-app/my-stack/PROD/dev-pol-id/p1"
        )
        .path("/developer-policy/guardian/janus-app/my-stack/PROD/dev-pol-id/")
        .policyName("p1")
        .description("")
        .build()

      toDeveloperPolicy(account, policy) shouldBe None
    }

    "should return None when description is whitespace" in {
      val policy = Policy
        .builder()
        .arn(
          "arn:aws:iam::123:policy/developer-policy/guardian/janus-app/my-stack/PROD/dev-pol-id/p1"
        )
        .path("/developer-policy/guardian/janus-app/my-stack/PROD/dev-pol-id/")
        .policyName("p1")
        .description("   ")
        .build()

      toDeveloperPolicy(account, policy) shouldBe None
    }
  }

  "developerPolicySlug" - {
    "always prefixes slug with DEVELOPER_POLICY_NAMESPACE_PREFIX" in {
      forAll(Gen.asciiPrintableStr) { rawPolicyName =>
        val slug = developerPolicySlug(rawPolicyName)
        slug.startsWith(
          DEVELOPER_POLICY_NAMESPACE_PREFIX
        ) shouldBe true
      }
    }

    "URL-encodes special characters" in {
      val testCases = List(
        ("policy.name", "policy.name"),
        ("policy,name", "policy%2Cname"),
        ("policy+name", "policy%2Bname"),
        ("policy@name", "policy%40name"),
        ("policy=name", "policy%3Dname"),
        ("policy name", "policy+name")
      )

      for ((rawPolicyName, expected) <- testCases) {
        val slug = developerPolicySlug(rawPolicyName)
        slug should endWith(s"-$expected")
      }
    }

    "produces different slugs for policy names that differ only in special characters" in {
      val policyNames =
        List("policy.name", "policy,name", "policy+name", "policy@name")
      val slugs = policyNames.map(developerPolicySlug)

      slugs.distinct.size shouldBe policyNames.size
    }
  }

  "developerPolicyDisplayName" - {
    "strips the namespace prefix" in {
      developerPolicyDisplayName("iam-mypolicy") shouldBe "mypolicy"
    }

    "URL-decodes encoded characters" in {
      val testCases = List(
        ("iam-policy.name", "policy.name"),
        ("iam-policy%2Cname", "policy,name"),
        ("iam-policy%2Bname", "policy+name"),
        ("iam-policy%40name", "policy@name"),
        ("iam-policy%3Dname", "policy=name"),
        ("iam-policy+name", "policy name")
      )

      for ((slug, expected) <- testCases) {
        developerPolicyDisplayName(slug) shouldBe expected
      }
    }

    "round-trips a slug produced by developerPolicySlug" in {
      forAll(Gen.asciiPrintableStr) { rawPolicyName =>
        val slug = developerPolicySlug(rawPolicyName)
        developerPolicyDisplayName(slug) shouldBe rawPolicyName
      }
    }
  }

  "toPermission" - {
    val developerPolicy = DeveloperPolicy(
      "arn:aws:iam::123:policy/guardian/test-repo/test-stack/PROD/dev-pol-id/p1",
      "p1",
      "dev-pol-id",
      "guardian/test-repo",
      "test-stack",
      "PROD",
      "A description",
      account
    )
    val grant = DeveloperPolicyGrant("grant", "dev-pol-id", shortTerm = false)

    "uses the policy slug as the permission label" in {
      val permission = toPermission(developerPolicy, grant)
      permission.label shouldBe developerPolicySlug(developerPolicy.policyName)
    }

    "uses the policy account" in {
      val permission = toPermission(developerPolicy, grant)
      permission.account shouldBe account
    }

    "uses the policy description" in {
      val permission = toPermission(developerPolicy, grant)
      permission.description shouldBe "A description"
    }

    "uses the policy ARN as the managed policy ARN" in {
      val permission = toPermission(developerPolicy, grant)
      permission.managedPolicyArns.value should contain(
        developerPolicy.policyArn.toString
      )
    }

    "maps shortTerm from the developer policy grant" - {
      "permission isn't short-term when grant isn't short-term" in {
        val longSessionPermission = toPermission(developerPolicy, grant)
        longSessionPermission.shortTerm shouldBe false
      }

      "permission is short-term when grant is short-term" in {
        val shortTermGrant = DeveloperPolicyGrant(
          "short-term-grant",
          "dev-pol-id",
          shortTerm = true
        )
        val shortSessionPermission =
          toPermission(developerPolicy, shortTermGrant)
        shortSessionPermission.shortTerm shouldBe true
      }
    }

    "property: permission id is composed of account key and policy slug" in {
      forAll(
        Gen.alphaNumStr.suchThat(_.nonEmpty),
        Gen.alphaNumStr.suchThat(_.nonEmpty)
      ) { (policyName, accountKey) =>
        val acc = AwsAccount("Test Account", accountKey)
        val pol = DeveloperPolicy(
          s"arn:aws:iam::123:policy/guardian/test-repo/test-stack/PROD/grant-id/$policyName",
          policyName,
          "grant-id",
          "guardian/test-repo",
          "test-stack",
          "PROD",
          "A description",
          acc
        )
        val permission = toPermission(pol, grant)
        permission.id shouldBe s"${acc.authConfigKey}-${developerPolicySlug(policyName)}"
      }
    }
  }

  "lookupDeveloperPolicyCacheStatus" - {
    val genAwsAccount: Gen[AwsAccount] =
      for {
        name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        key <- Gen.alphaNumStr.suchThat(_.nonEmpty)
      } yield AwsAccount(name, key)

    val genInstant: Gen[Instant] =
      Gen.posNum[Long].map(Instant.ofEpochMilli)

    val genFailureSnapshot: Gen[FailureSnapshot] =
      for {
        msg <- Gen.alphaStr.suchThat(_.nonEmpty)
        failureInstant <- genInstant
      } yield FailureSnapshot(msg, failureInstant)

    val genDeveloperPolicy: Gen[DeveloperPolicy] =
      for {
        account <- genAwsAccount
        policyName <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        grantId <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        sourceRepo <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        stackName <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        stage <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        description <- Gen.alphaStr.suchThat(_.nonEmpty)
      } yield DeveloperPolicy(
        s"arn:aws:iam::123456789012:policy/guardian/$sourceRepo/$stackName/$stage/$grantId/$policyName",
        policyName,
        grantId,
        s"guardian/$sourceRepo",
        stackName,
        stage,
        description,
        account
      )

    val genDeveloperPolicySnapshot: Gen[DeveloperPolicySnapshot] =
      for {
        policies <- Gen.nonEmptyListOf(genDeveloperPolicy)
        snapshotInstant <- genInstant
      } yield DeveloperPolicySnapshot(policies, snapshotInstant)

    val genSuccessStatus: Gen[AwsAccountDeveloperPolicyStatus] =
      genDeveloperPolicySnapshot.map(AwsAccountDeveloperPolicyStatus.success)

    // Covers both failure-with-stale-cache and failure-with-no-cache
    val genFailureStatus: Gen[AwsAccountDeveloperPolicyStatus] =
      for {
        cached <- Gen.option(genDeveloperPolicySnapshot)
        failure <- genFailureSnapshot
      } yield AwsAccountDeveloperPolicyStatus.failure(cached, failure)

    val genNonFailureStatus: Gen[AwsAccountDeveloperPolicyStatus] =
      Gen.oneOf(
        genSuccessStatus,
        Gen.const(AwsAccountDeveloperPolicyStatus.empty)
      )

    "Failure takes precedence regardless of what other statuses are present" in {
      forAll(
        Gen.mapOf(Gen.zip(genAwsAccount, genNonFailureStatus)),
        Gen.zip(genAwsAccount, genFailureStatus),
        Arbitrary.arbitrary[Boolean]
      ) { (otherStatuses, accountAndStatus, serviceEnabled) =>
        val (failingAccount, failureStatus) = accountAndStatus
        val statuses = otherStatuses + (failingAccount -> failureStatus)

        DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
          statuses,
          serviceEnabled
        ) shouldBe DeveloperPolicyCacheStatus.Failure
      }
    }

    "returns Disabled (not Empty) when service is disabled, regardless of whether snapshots are missing" in {
      forAll(
        Gen.mapOf(Gen.zip(genAwsAccount, genNonFailureStatus))
      ) { statuses =>
        DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
          statuses,
          serviceEnabled = false
        ) shouldBe DeveloperPolicyCacheStatus.Disabled
      }
    }

    "returns Empty when enabled and at least one account has an empty status" in {
      forAll(
        Gen.mapOf(Gen.zip(genAwsAccount, genSuccessStatus)),
        genAwsAccount
      ) { (successStatuses, emptyAccount) =>
        val statuses =
          successStatuses + (emptyAccount -> AwsAccountDeveloperPolicyStatus.empty)

        DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
          statuses,
          serviceEnabled = true
        ) shouldBe DeveloperPolicyCacheStatus.Empty
      }
    }

    "returns Success when enabled and all accounts have a policySnapshot" in {
      forAll(
        Gen.mapOf(Gen.zip(genAwsAccount, genSuccessStatus))
      ) { statuses =>
        DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
          statuses,
          serviceEnabled = true
        ) shouldBe DeveloperPolicyCacheStatus.Success
      }
    }

    "treats Some(DeveloperPolicySnapshot(Nil, ...)) as present (not Empty)" in {
      val snapshotWithNoPolicies = DeveloperPolicySnapshot(Nil, Instant.now())
      val statuses = Map(
        AwsAccount("Account A", "a") -> AwsAccountDeveloperPolicyStatus.success(
          snapshotWithNoPolicies
        )
      )

      DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
        statuses,
        serviceEnabled = true
      ) shouldBe DeveloperPolicyCacheStatus.Success
    }
  }
}
