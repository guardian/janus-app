package logic

import com.gu.janus.model.{AwsAccount, ProvisionedRole}
import models.{
  AwsAccountIamRoleInfoStatus,
  FailureSnapshot,
  IamRoleInfo,
  IamRoleInfoSnapshot
}
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import software.amazon.awssdk.services.iam.model.{Role, Tag}

import java.time.Instant

class ProvisionedRolesTest
    extends AnyFreeSpec
    with Matchers
    with ScalaCheckPropertyChecks {

  private val provisionedRoleTagKey = "ProvisionedRole"
  private val friendlyNameTagKey = "FriendlyName"
  private val descriptionTagKey = "Description"

  private val timestamp = Instant.now()

  private val account =
    AwsAccount(name = "Account Name", authConfigKey = "accId")

  private val role: Role =
    Role.builder().arn("arn:aws:iam::123:role/r1").build()

  private def createTag(key: String, value: String): Tag =
    Tag.builder().key(key).value(value).build()

  "getIamRolesByProvisionedRole" - {
    "should return empty list when cache is empty" in {
      val result = ProvisionedRoles.getIamRolesByProvisionedRole(
        Map.empty,
        ProvisionedRole("Test Role", "test-role")
      )

      result shouldBe empty
    }

    "should return empty list when no accounts have snapshots" in {
      val cache = Map(
        AwsAccount("123", "acc1") -> AwsAccountIamRoleInfoStatus(None, None),
        AwsAccount("456", "acc2") -> AwsAccountIamRoleInfoStatus(
          None,
          Some(FailureSnapshot("error", timestamp))
        )
      )

      val result = ProvisionedRoles.getIamRolesByProvisionedRole(
        cache,
        ProvisionedRole("Test Role", "test-role")
      )

      result shouldBe empty
    }

    "should return empty list when no roles match the tag" in {
      val cache = Map(
        AwsAccount("123", "acc") -> AwsAccountIamRoleInfoStatus(
          Some(
            IamRoleInfoSnapshot(
              List(
                IamRoleInfo(
                  "arn:aws:iam::123:role/r1",
                  "other-tag",
                  None,
                  None,
                  account
                ),
                IamRoleInfo(
                  "arn:aws:iam::123:role/r2",
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

      val result = ProvisionedRoles.getIamRolesByProvisionedRole(
        cache,
        ProvisionedRole("Test Role", "test-role")
      )

      result shouldBe empty
    }

    "should return single matching role from single account" in {
      val matchingRole =
        IamRoleInfo(
          "arn:aws:iam::123:role/r1",
          "test-role",
          None,
          None,
          account
        )
      val cache = Map(
        AwsAccount("123", "acc") -> AwsAccountIamRoleInfoStatus(
          Some(IamRoleInfoSnapshot(List(matchingRole), timestamp)),
          None
        )
      )

      val result = ProvisionedRoles.getIamRolesByProvisionedRole(
        cache,
        ProvisionedRole("Test Role", "test-role")
      )

      result should contain only matchingRole
    }

    "should filter matching roles from mixed list" in {
      val matching =
        IamRoleInfo(
          "arn:aws:iam::123:role/r1",
          "test-role",
          None,
          None,
          account
        )
      val cache = Map(
        AwsAccount("123", "acc") -> AwsAccountIamRoleInfoStatus(
          Some(
            IamRoleInfoSnapshot(
              List(
                IamRoleInfo(
                  "arn:aws:iam::123:role/r0",
                  "other",
                  None,
                  None,
                  account
                ),
                matching,
                IamRoleInfo(
                  "arn:aws:iam::123:role/r2",
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

      val result = ProvisionedRoles.getIamRolesByProvisionedRole(
        cache,
        ProvisionedRole("Test Role", "test-role")
      )

      result should contain only matching
    }

    "should aggregate matching roles from multiple accounts" in {
      val role1 =
        IamRoleInfo(
          "arn:aws:iam::111:role/r1",
          "test-role",
          None,
          None,
          account
        )
      val role2 =
        IamRoleInfo(
          "arn:aws:iam::222:role/r2",
          "test-role",
          None,
          None,
          account
        )
      val cache = Map(
        AwsAccount("111", "acc1") -> AwsAccountIamRoleInfoStatus(
          Some(IamRoleInfoSnapshot(List(role1), timestamp)),
          None
        ),
        AwsAccount("222", "acc2") -> AwsAccountIamRoleInfoStatus(
          Some(IamRoleInfoSnapshot(List(role2), timestamp)),
          None
        )
      )

      val result = ProvisionedRoles.getIamRolesByProvisionedRole(
        cache,
        ProvisionedRole("Test Role", "test-role")
      )

      result should contain theSameElementsAs List(role1, role2)
    }

    "should handle accounts with mixed snapshot states" in {
      val matchingRole =
        IamRoleInfo(
          "arn:aws:iam::111:role/r1",
          "test-role",
          None,
          None,
          account
        )
      val cache = Map(
        AwsAccount("111", "acc1") -> AwsAccountIamRoleInfoStatus(
          Some(IamRoleInfoSnapshot(List(matchingRole), timestamp)),
          None
        ),
        AwsAccount("222", "acc2") -> AwsAccountIamRoleInfoStatus(None, None),
        AwsAccount("333", "acc3") -> AwsAccountIamRoleInfoStatus(
          None,
          Some(FailureSnapshot("error", timestamp))
        )
      )

      val result = ProvisionedRoles.getIamRolesByProvisionedRole(
        cache,
        ProvisionedRole("Test Role", "test-role")
      )

      result should contain only matchingRole
    }

    "property: result should only contain roles with matching tag" in {
      forAll(Gen.listOfN(5, Gen.alphaStr), Gen.alphaStr) {
        (tags: List[String], targetTag: String) =>
          val roles = tags.zipWithIndex.map { case (tag, idx) =>
            IamRoleInfo(
              s"arn:aws:iam::123:role/r$idx",
              tag,
              None,
              None,
              account
            )
          }
          val cache = Map(
            AwsAccount("123", "acc") -> AwsAccountIamRoleInfoStatus(
              Some(IamRoleInfoSnapshot(roles, timestamp)),
              None
            )
          )

          val result = ProvisionedRoles.getIamRolesByProvisionedRole(
            cache,
            ProvisionedRole(targetTag, "Test")
          )

          result.forall(_.provisionedRoleTagValue == targetTag) shouldBe true
      }
    }
  }

  "toRoleInfo" - {
    "should return None when provisioned role tag is absent" in {
      val tags = Set(
        createTag(friendlyNameTagKey, "Name"),
        createTag(descriptionTagKey, "Desc")
      )

      val result = ProvisionedRoles.toRoleInfo(
        account,
        role,
        tags,
        provisionedRoleTagKey,
        friendlyNameTagKey,
        descriptionTagKey
      )

      result shouldBe None
    }

    "should return IamRoleInfo with only required fields when optional tags absent" in {
      val tags = Set(createTag(provisionedRoleTagKey, "test-role"))

      val result = ProvisionedRoles.toRoleInfo(
        account,
        role,
        tags,
        provisionedRoleTagKey,
        friendlyNameTagKey,
        descriptionTagKey
      )

      result shouldBe Some(
        IamRoleInfo(
          "arn:aws:iam::123:role/r1",
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

      val result = ProvisionedRoles.toRoleInfo(
        account,
        role,
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

      val result = ProvisionedRoles.toRoleInfo(
        account,
        role,
        tags,
        provisionedRoleTagKey,
        friendlyNameTagKey,
        descriptionTagKey
      )

      result.flatMap(_.description) shouldBe Some("Description")
    }

    "should be case-sensitive for tag keys" in {
      val tags = Set(createTag("provisionedrole", "test-role"))

      val result = ProvisionedRoles.toRoleInfo(
        account,
        role,
        tags,
        "ProvisionedRole",
        friendlyNameTagKey,
        descriptionTagKey
      )

      result shouldBe None
    }

    "should handle empty tag set" in {
      val result = ProvisionedRoles.toRoleInfo(
        account,
        role,
        Set.empty,
        provisionedRoleTagKey,
        friendlyNameTagKey,
        descriptionTagKey
      )

      result shouldBe None
    }
  }

  "provisionedRoleNameFromArn" - {
    "should extract role name from ARN with janus/discoverable prefix" in {
      val arn = software.amazon.awssdk.arns.Arn.fromString(
        "arn:aws:iam::123456789:role/gu/janus/discoverable/my-role"
      )

      val result = ProvisionedRoles.provisionedRoleNameFromArn(arn)

      result shouldBe Some("my-role")
    }

    "should return None when ARN path does not match a Janus provisioned role" in {
      val arn = software.amazon.awssdk.arns.Arn.fromString(
        "arn:aws:iam::123456789:role/different/path/role-name"
      )

      val result = ProvisionedRoles.provisionedRoleNameFromArn(arn)

      result shouldBe None
    }

    "should return None when ARN has no qualifier" in {
      val arn = software.amazon.awssdk.arns.Arn.fromString(
        "arn:aws:iam::123456789:role"
      )

      val result = ProvisionedRoles.provisionedRoleNameFromArn(arn)

      result shouldBe None
    }
  }

  "provisionedRoleLinkFromArn" - {
    "should generate console link from ARN" in {
      val arn = software.amazon.awssdk.arns.Arn.fromString(
        "arn:aws:iam::012345678901:role/gu/janus/discoverable/my-role"
      )

      val result = ProvisionedRoles.provisionedRoleLinkFromArn(arn)

      result shouldBe Some(
        "https://console.aws.amazon.com/iam/home#/roles/details/my-role"
      )
    }

    "should return None when ARN has no qualifier" in {
      val arn = software.amazon.awssdk.arns.Arn.fromString(
        "arn:aws:iam::123456789:role"
      )

      val result = ProvisionedRoles.provisionedRoleLinkFromArn(arn)

      result shouldBe None
    }
  }
}
