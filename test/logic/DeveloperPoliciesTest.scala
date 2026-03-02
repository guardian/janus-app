package logic

import com.gu.janus.model.AwsAccount
import logic.DeveloperPolicies.{
  DEVELOPER_POLICY_NAMESPACE_PREFIX,
  developerPolicySlug,
  toDeveloperPolicy,
  toPermission
}
import models.DeveloperPolicy
import org.scalacheck.Gen
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

      val result = toDeveloperPolicy(account, policy)

      result shouldBe None
    }

    "should return developer policy with only required fields when description is absent" in {
      val policy = Policy
        .builder()
        .arn("arn:aws:iam::123:policy/developer-policy/dev-pol-id/p1")
        .path("/developer-policy/dev-pol-id/")
        .policyName("p1")
        .build()
      val result = toDeveloperPolicy(account, policy)

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
      val result = toDeveloperPolicy(account, policy)

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
      val result = toDeveloperPolicy(account, policy)

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
      val result = toDeveloperPolicy(account, policy)

      result.flatMap(_.description) shouldBe Some("Description")
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

  "toPermission" - {
    val developerPolicy = DeveloperPolicy(
      "arn:aws:iam::123:policy/developer-policy/dev-pol-id/p1",
      "p1",
      "dev-pol-id",
      Some("A description"),
      account
    )

    "uses the policy slug as the permission label" in {
      val permission = toPermission(developerPolicy)
      permission.label shouldBe developerPolicySlug(developerPolicy.policyName)
    }

    "uses the policy account" in {
      val permission = toPermission(developerPolicy)
      permission.account shouldBe account
    }

    "uses the policy description when present" in {
      val permission = toPermission(developerPolicy)
      permission.description shouldBe "A description"
    }

    "uses a fallback description when none is present" in {
      val noDesc = developerPolicy.copy(description = None)
      val permission = toPermission(noDesc)
      permission.description should not be empty
    }

    "uses the policy ARN as the managed policy ARN" in {
      val permission = toPermission(developerPolicy)
      permission.managedPolicyArns.value should contain(
        developerPolicy.policyArn.toString
      )
    }

    "property: permission id is composed of account key and policy slug" in {
      forAll(
        Gen.alphaNumStr.suchThat(_.nonEmpty),
        Gen.alphaNumStr.suchThat(_.nonEmpty)
      ) { (policyName, accountKey) =>
        val acc = AwsAccount("Test Account", accountKey)
        val pol = DeveloperPolicy(
          s"arn:aws:iam::123:policy/developer-policy/grant-id/$policyName",
          policyName,
          "grant-id",
          None,
          acc
        )
        val permission = toPermission(pol)
        permission.id shouldBe s"${acc.authConfigKey}-${developerPolicySlug(policyName)}"
      }
    }
  }
}
