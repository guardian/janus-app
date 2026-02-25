package logic

import com.gu.janus.model.{AwsAccount, DeveloperPolicyGrant}
import models.{
  AwsAccountDeveloperPolicyStatus,
  DeveloperPolicy,
  DeveloperPolicySnapshot,
  FailureSnapshot
}
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import software.amazon.awssdk.services.iam.model.Policy

import java.time.Instant

class DeveloperPoliciesTest
    extends AnyFreeSpec
    with Matchers
    with ScalaCheckPropertyChecks {

  private val timestamp = Instant.now()

  private val account =
    AwsAccount(name = "Account Name", authConfigKey = "accId")

  private val policy: Policy =
    Policy
      .builder()
      .arn("arn:aws:iam::123:policy/developer-policy/dev-pol-id/p1")
      .path("/developer-policy/dev-pol-id/")
      .policyName("p1")
      .description("Description")
      .build()

  "toDeveloperPolicy" - {
    "should return None when developer policy grant ID is absent" in {
      val policy = Policy
        .builder()
        .arn("arn:aws:iam::123:policy/developer-policy/p1")
        .path("/developer-policy/")
        .policyName("p1")
        .build()

      val result = DeveloperPolicies.toDeveloperPolicy(account, policy)

      result shouldBe None
    }

    "should return developer policy with only required fields when description is absent" in {
      val policy = Policy
        .builder()
        .arn("arn:aws:iam::123:policy/developer-policy/dev-pol-id/p1")
        .path("/developer-policy/dev-pol-id/")
        .policyName("p1")
        .build()
      val result = DeveloperPolicies.toDeveloperPolicy(account, policy)

      result shouldBe Some(
        DeveloperPolicy(
          "arn:aws:iam::123:policy/developer-policy/dev-pol-id/p1",
          "p1",
          "dev-pol-id",
          None,
          account
        )
      )
    }

    "should return developer policy with only required fields when description is empty" in {
      val policy = Policy
        .builder()
        .arn("arn:aws:iam::123:policy/developer-policy/dev-pol-id/p1")
        .path("/developer-policy/dev-pol-id/")
        .policyName("p1")
        .description("")
        .build()
      val result = DeveloperPolicies.toDeveloperPolicy(account, policy)

      result shouldBe Some(
        DeveloperPolicy(
          "arn:aws:iam::123:policy/developer-policy/dev-pol-id/p1",
          "p1",
          "dev-pol-id",
          None,
          account
        )
      )
    }

    "should return developer policy with only required fields when description is whitespace" in {
      val policy = Policy
        .builder()
        .arn("arn:aws:iam::123:policy/developer-policy/dev-pol-id/p1")
        .path("/developer-policy/dev-pol-id/")
        .policyName("p1")
        .description("   ")
        .build()
      val result = DeveloperPolicies.toDeveloperPolicy(account, policy)

      result shouldBe Some(
        DeveloperPolicy(
          "arn:aws:iam::123:policy/developer-policy/dev-pol-id/p1",
          "p1",
          "dev-pol-id",
          None,
          account
        )
      )
    }

    "should include description when present" in {
      val result = DeveloperPolicies.toDeveloperPolicy(account, policy)

      result.flatMap(_.description) shouldBe Some("Description")
    }
  }

  "developerPolicySlug" - {
    "always prefixes slug with DEVELOPER_POLICY_NAMESPACE_PREFIX" in {
      forAll(Gen.asciiPrintableStr) { rawPolicyName =>
        val slug = DeveloperPolicies.developerPolicySlug(rawPolicyName, account)
        slug.startsWith(
          DeveloperPolicies.DEVELOPER_POLICY_NAMESPACE_PREFIX
        ) shouldBe true
      }
    }

    "always includes account auth config key in slug" in {
      forAll(Gen.asciiPrintableStr) { rawPolicyName =>
        val slug = DeveloperPolicies.developerPolicySlug(rawPolicyName, account)
        slug.contains(account.authConfigKey) shouldBe true
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
        val slug = DeveloperPolicies.developerPolicySlug(rawPolicyName, account)
        slug should endWith(s"-$expected")
      }
    }

    "produces different slugs for policy names that differ only in special characters" in {
      val policyNames =
        List("policy.name", "policy,name", "policy+name", "policy@name")
      val slugs =
        policyNames.map(DeveloperPolicies.developerPolicySlug(_, account))

      slugs.distinct.size shouldBe policyNames.size
    }
  }
}
