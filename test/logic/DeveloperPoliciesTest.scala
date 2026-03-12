package logic

import com.gu.janus.model.AwsAccount
import logic.DeveloperPolicies.{
  DEVELOPER_POLICY_NAMESPACE_PREFIX,
  developerPolicySlug,
  toDeveloperPolicy,
  toPermission
}
import models.{
  AwsAccountDeveloperPolicyStatus,
  DeveloperPolicy,
  DeveloperPolicyCacheStatus,
  DeveloperPolicySnapshot,
  FailureSnapshot
}
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

  "lookupDeveloperPolicyCacheStatus" - {
    val now = Instant.now()
    val accountA = AwsAccount("Account A", "a")
    val accountB = AwsAccount("Account B", "b")
    val samplePolicy =
      DeveloperPolicy(
        "arn:aws:iam::123:policy/developer-policy/grant-id/policy",
        "policy",
        "grant-id",
        None,
        accountA
      )
    val nonEmptySnapshot =
      DeveloperPolicySnapshot(List(samplePolicy), now)
    val nonEmptyStatus =
      AwsAccountDeveloperPolicyStatus.success(nonEmptySnapshot)

    "returns Failure if any account has a failure when enabled " in {
      val failure = AwsAccountDeveloperPolicyStatus.failure(
        cachedPolicySnapshot = Some(nonEmptySnapshot),
        failureStatus =
          FailureSnapshot("Failed to fetch developer policies", now)
      )
      val statuses = Map(accountA -> failure, accountB -> nonEmptyStatus)

      DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
        statuses,
        serviceEnabled = true
      ) shouldBe DeveloperPolicyCacheStatus.Failure
    }

    "Failure takes precedence over Empty when enabled" in {
      val failure = AwsAccountDeveloperPolicyStatus.failure(
        cachedPolicySnapshot = None,
        failureStatus =
          FailureSnapshot("Failed to fetch developer policies", now)
      )
      val emptyStatus = AwsAccountDeveloperPolicyStatus.empty
      val statuses = Map(accountA -> failure, accountB -> emptyStatus)

      DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
        statuses,
        serviceEnabled = true
      ) shouldBe DeveloperPolicyCacheStatus.Failure
    }

    "returns Disabled when service is disabled even if some policySnapshots are empty" in {
      val emptyStatus = AwsAccountDeveloperPolicyStatus.empty
      val statuses = Map(accountA -> emptyStatus, accountB -> nonEmptyStatus)

      DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
        statuses,
        serviceEnabled = false
      ) shouldBe DeveloperPolicyCacheStatus.Disabled
    }

    "returns Empty when enabled and any account has no policySnapshot" in {
      val emptyStatus = AwsAccountDeveloperPolicyStatus.empty
      val statuses = Map(accountA -> emptyStatus, accountB -> nonEmptyStatus)

      DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
        statuses,
        serviceEnabled = true
      ) shouldBe DeveloperPolicyCacheStatus.Empty
    }

    "returns Success when enabled, no failures, and every account has a policySnapshot" in {
      val statuses = Map(accountA -> nonEmptyStatus, accountB -> nonEmptyStatus)
      DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
        statuses,
        serviceEnabled = true
      ) shouldBe DeveloperPolicyCacheStatus.Success
    }

    "empty map case: Success when enabled, Disabled when disabled" in {
      DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
        Map.empty,
        serviceEnabled = true
      ) shouldBe DeveloperPolicyCacheStatus.Success
      DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
        Map.empty,
        serviceEnabled = false
      ) shouldBe DeveloperPolicyCacheStatus.Disabled
    }

    "treats Some(DeveloperPolicySnapshot(Nil, ...)) as present (not Empty)" in {
      val snapshotWithNoPolicies = DeveloperPolicySnapshot(Nil, now)
      val statusWithEmptyListSnapshot =
        AwsAccountDeveloperPolicyStatus.success(snapshotWithNoPolicies)
      val statuses =
        Map(accountA -> statusWithEmptyListSnapshot, accountB -> nonEmptyStatus)

      DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
        statuses,
        serviceEnabled = true
      ) shouldBe DeveloperPolicyCacheStatus.Success
    }
  }
}
