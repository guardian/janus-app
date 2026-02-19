package logic

import com.gu.janus.model.{AwsAccount, ProvisionedRole}
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

  private val provisionedRoleId = "dev-pol-id"

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

  "getDeveloperPoliciesByProvisionedRole" - {
    "should return empty list when cache is empty" in {
      val result = DeveloperPolicies.getDeveloperPoliciesByProvisionedRole(
        Map.empty,
        ProvisionedRole("Test Role", "dev-pol-id")
      )

      result shouldBe empty
    }

    "should return empty list when no accounts have snapshots" in {
      val cache = Map(
        AwsAccount("123", "acc1") -> AwsAccountDeveloperPolicyStatus(
          None,
          None
        ),
        AwsAccount("456", "acc2") -> AwsAccountDeveloperPolicyStatus(
          None,
          Some(FailureSnapshot("error", timestamp))
        )
      )

      val result = DeveloperPolicies.getDeveloperPoliciesByProvisionedRole(
        cache,
        ProvisionedRole("Test Role", "dev-pol-id")
      )

      result shouldBe empty
    }

    "should return empty list when no policies match the developer policy ID" in {
      val cache = Map(
        AwsAccount("123", "acc") -> AwsAccountDeveloperPolicyStatus(
          Some(
            DeveloperPolicySnapshot(
              List(
                DeveloperPolicy(
                  "arn:aws:iam::123:policy/developer-policy/dev-pol-id/p1",
                  "p1",
                  "other-id",
                  None,
                  account
                ),
                DeveloperPolicy(
                  "arn:aws:iam::123:policy/developer-policy/dev-pol-id/p2",
                  "p2",
                  "different-id",
                  None,
                  account
                )
              ),
              timestamp
            )
          ),
          None
        )
      )

      val result = DeveloperPolicies.getDeveloperPoliciesByProvisionedRole(
        cache,
        ProvisionedRole("Test Role", "dev-pol-id")
      )

      result shouldBe empty
    }

    "should return single matching policy from single account" in {
      val matchingPolicy =
        DeveloperPolicy(
          "arn:aws:iam::123:policy/developer-policy/dev-pol-id/p1",
          "p1",
          "dev-pol-id",
          None,
          account
        )
      val cache = Map(
        AwsAccount("123", "acc") -> AwsAccountDeveloperPolicyStatus(
          Some(DeveloperPolicySnapshot(List(matchingPolicy), timestamp)),
          None
        )
      )

      val result = DeveloperPolicies.getDeveloperPoliciesByProvisionedRole(
        cache,
        ProvisionedRole("Test Role", "dev-pol-id")
      )

      result should contain only matchingPolicy
    }

    "should filter matching policies from mixed list" in {
      val matching =
        DeveloperPolicy(
          "arn:aws:iam::123:policy/developer-policy/dev-pol-id/p1",
          "p1",
          "dev-pol-id",
          None,
          account
        )
      val cache = Map(
        AwsAccount("123", "acc") -> AwsAccountDeveloperPolicyStatus(
          Some(
            DeveloperPolicySnapshot(
              List(
                DeveloperPolicy(
                  "arn:aws:iam::123:policy/developer-policy/dev-pol-id/p0",
                  "p0",
                  "other",
                  None,
                  account
                ),
                matching,
                DeveloperPolicy(
                  "arn:aws:iam::123:policy/developer-policy/dev-pol-id/p2",
                  "r2",
                  "different",
                  None,
                  account
                )
              ),
              timestamp
            )
          ),
          None
        )
      )

      val result = DeveloperPolicies.getDeveloperPoliciesByProvisionedRole(
        cache,
        ProvisionedRole("Test Role", "dev-pol-id")
      )

      result should contain only matching
    }

    "should aggregate matching policies from multiple accounts" in {
      val policy1 =
        DeveloperPolicy(
          "arn:aws:iam::111:policy/developer-policy/dev-pol-id/p1",
          "p1",
          "dev-pol-id",
          None,
          account
        )
      val policy2 =
        DeveloperPolicy(
          "arn:aws:iam::222:policy/developer-policy/dev-pol-id/p2",
          "p2",
          "dev-pol-id",
          None,
          account
        )
      val cache = Map(
        AwsAccount("111", "acc1") -> AwsAccountDeveloperPolicyStatus(
          Some(DeveloperPolicySnapshot(List(policy1), timestamp)),
          None
        ),
        AwsAccount("222", "acc2") -> AwsAccountDeveloperPolicyStatus(
          Some(DeveloperPolicySnapshot(List(policy2), timestamp)),
          None
        )
      )

      val result = DeveloperPolicies.getDeveloperPoliciesByProvisionedRole(
        cache,
        ProvisionedRole("Test Role", "dev-pol-id")
      )

      result should contain theSameElementsAs List(policy1, policy2)
    }

    "should handle accounts with mixed snapshot states" in {
      val matchingPolicy =
        DeveloperPolicy(
          "arn:aws:iam::111:policy/developer-policy/dev-pol-id/p1",
          "p1",
          "dev-pol-id",
          None,
          account
        )
      val cache = Map(
        AwsAccount("111", "acc1") -> AwsAccountDeveloperPolicyStatus(
          Some(DeveloperPolicySnapshot(List(matchingPolicy), timestamp)),
          None
        ),
        AwsAccount("222", "acc2") -> AwsAccountDeveloperPolicyStatus(
          None,
          None
        ),
        AwsAccount("333", "acc3") -> AwsAccountDeveloperPolicyStatus(
          None,
          Some(FailureSnapshot("error", timestamp))
        )
      )

      val result = DeveloperPolicies.getDeveloperPoliciesByProvisionedRole(
        cache,
        ProvisionedRole("Test Role", "dev-pol-id")
      )

      result should contain only matchingPolicy
    }

    "property: result should only contain policies with matching developer policy ID" in {
      forAll(Gen.listOfN(5, Gen.alphaStr), Gen.alphaStr) {
        (ids: List[String], targetId: String) =>
          val policies = ids.zipWithIndex.map { case (id, idx) =>
            DeveloperPolicy(
              s"arn:aws:iam::123:policy/developer-policy/dev-pol-id/p$idx",
              s"p$idx",
              id,
              None,
              account
            )
          }
          val cache = Map(
            AwsAccount("123", "acc") -> AwsAccountDeveloperPolicyStatus(
              Some(DeveloperPolicySnapshot(policies, timestamp)),
              None
            )
          )

          val result = DeveloperPolicies.getDeveloperPoliciesByProvisionedRole(
            cache,
            ProvisionedRole("Test", targetId)
          )

          result.forall(_.provisionedRoleId == targetId) shouldBe true
      }
    }
  }

  "toDeveloperPolicy" - {
    "should return None when provisioned role ID is absent" in {
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
