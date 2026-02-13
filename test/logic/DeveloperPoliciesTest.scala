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
import software.amazon.awssdk.services.iam.model.{Policy, Tag}

import java.time.Instant

class DeveloperPoliciesTest
    extends AnyFreeSpec
    with Matchers
    with ScalaCheckPropertyChecks {

  private val provisionedRoleTagKey = "ProvisionedRole"
  private val friendlyNameTagKey = "FriendlyName"
  private val descriptionTagKey = "Description"

  private val timestamp = Instant.now()

  private val account =
    AwsAccount(name = "Account Name", authConfigKey = "accId")

  private val policy: Policy =
    Policy
      .builder()
      .arn("arn:aws:iam::123:policy/p1")
      .policyName("p1")
      .build()

  private def createTag(key: String, value: String): Tag =
    Tag.builder().key(key).value(value).build()

  "getDeveloperPoliciesByProvisionedRole" - {
    "should return empty list when cache is empty" in {
      val result = DeveloperPolicies.getDeveloperPoliciesByProvisionedRole(
        Map.empty,
        ProvisionedRole("Test Role", "test-role")
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
        ProvisionedRole("Test Role", "test-role")
      )

      result shouldBe empty
    }

    "should return empty list when no policies match the tag" in {
      val cache = Map(
        AwsAccount("123", "acc") -> AwsAccountDeveloperPolicyStatus(
          Some(
            DeveloperPolicySnapshot(
              List(
                DeveloperPolicy(
                  "arn:aws:iam::123:policy/p1",
                  "p1",
                  "other-tag",
                  None,
                  None,
                  account
                ),
                DeveloperPolicy(
                  "arn:aws:iam::123:policy/p2",
                  "p2",
                  "different-tag",
                  None,
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
        ProvisionedRole("Test Role", "test-role")
      )

      result shouldBe empty
    }

    "should return single matching policy from single account" in {
      val matchingPolicy =
        DeveloperPolicy(
          "arn:aws:iam::123:policy/p1",
          "p1",
          "test-role",
          None,
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
        ProvisionedRole("Test Role", "test-role")
      )

      result should contain only matchingPolicy
    }

    "should filter matching policies from mixed list" in {
      val matching =
        DeveloperPolicy(
          "arn:aws:iam::123:policy/p1",
          "p1",
          "test-role",
          None,
          None,
          account
        )
      val cache = Map(
        AwsAccount("123", "acc") -> AwsAccountDeveloperPolicyStatus(
          Some(
            DeveloperPolicySnapshot(
              List(
                DeveloperPolicy(
                  "arn:aws:iam::123:policy/p0",
                  "p0",
                  "other",
                  None,
                  None,
                  account
                ),
                matching,
                DeveloperPolicy(
                  "arn:aws:iam::123:policy/p2",
                  "r2",
                  "different",
                  None,
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
        ProvisionedRole("Test Role", "test-role")
      )

      result should contain only matching
    }

    "should aggregate matching policies from multiple accounts" in {
      val policy1 =
        DeveloperPolicy(
          "arn:aws:iam::111:policy/p1",
          "p1",
          "test-role",
          None,
          None,
          account
        )
      val policy2 =
        DeveloperPolicy(
          "arn:aws:iam::222:policy/p2",
          "p2",
          "test-role",
          None,
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
        ProvisionedRole("Test Role", "test-role")
      )

      result should contain theSameElementsAs List(policy1, policy2)
    }

    "should handle accounts with mixed snapshot states" in {
      val matchingPolicy =
        DeveloperPolicy(
          "arn:aws:iam::111:policy/p1",
          "p1",
          "test-role",
          None,
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
        ProvisionedRole("Test Role", "test-role")
      )

      result should contain only matchingPolicy
    }

    "property: result should only contain policies with matching tag" in {
      forAll(Gen.listOfN(5, Gen.alphaStr), Gen.alphaStr) {
        (tags: List[String], targetTag: String) =>
          val policies = tags.zipWithIndex.map { case (tag, idx) =>
            DeveloperPolicy(
              s"arn:aws:iam::123:policy/p$idx",
              s"p$idx",
              tag,
              None,
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
            ProvisionedRole(targetTag, "Test")
          )

          result.forall(_.provisionedRoleTagValue == targetTag) shouldBe true
      }
    }
  }

  "toDeveloperPolicy" - {
    "should return None when provisioned role tag is absent" in {
      val tags = Set(
        createTag(friendlyNameTagKey, "Name"),
        createTag(descriptionTagKey, "Desc")
      )

      val result = DeveloperPolicies.toDeveloperPolicy(
        account,
        policy,
        tags,
        provisionedRoleTagKey,
        friendlyNameTagKey,
        descriptionTagKey
      )

      result shouldBe None
    }

    "should return developer policy with only required fields when optional tags absent" in {
      val tags = Set(createTag(provisionedRoleTagKey, "test-role"))

      val result = DeveloperPolicies.toDeveloperPolicy(
        account,
        policy,
        tags,
        provisionedRoleTagKey,
        friendlyNameTagKey,
        descriptionTagKey
      )

      result shouldBe Some(
        DeveloperPolicy(
          "arn:aws:iam::123:policy/p1",
          "p1",
          "test-role",
          None,
          None,
          account
        )
      )
    }

    "should include friendly name when present" in {
      val tags = Set(
        createTag(provisionedRoleTagKey, "test-role"),
        createTag(friendlyNameTagKey, "Friendly")
      )

      val result = DeveloperPolicies.toDeveloperPolicy(
        account,
        policy,
        tags,
        provisionedRoleTagKey,
        friendlyNameTagKey,
        descriptionTagKey
      )

      result.flatMap(_.friendlyName) shouldBe Some("Friendly")
    }

    "should include description when present" in {
      val tags = Set(
        createTag(provisionedRoleTagKey, "test-role"),
        createTag(descriptionTagKey, "Description")
      )

      val result = DeveloperPolicies.toDeveloperPolicy(
        account,
        policy,
        tags,
        provisionedRoleTagKey,
        friendlyNameTagKey,
        descriptionTagKey
      )

      result.flatMap(_.description) shouldBe Some("Description")
    }

    "should be case-sensitive for tag keys" in {
      val tags = Set(createTag("provisionedrole", "test-role"))

      val result = DeveloperPolicies.toDeveloperPolicy(
        account,
        policy,
        tags,
        "ProvisionedRole",
        friendlyNameTagKey,
        descriptionTagKey
      )

      result shouldBe None
    }

    "should handle empty tag set" in {
      val result = DeveloperPolicies.toDeveloperPolicy(
        account,
        policy,
        Set.empty,
        provisionedRoleTagKey,
        friendlyNameTagKey,
        descriptionTagKey
      )

      result shouldBe None
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
