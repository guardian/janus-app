package models

import com.gu.janus.model.AwsAccount
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import software.amazon.awssdk.services.iam.model.{Role, Tag}

import java.time.Instant

class IamRoleInfoTest
    extends AnyFreeSpec
    with Matchers
    with ScalaCheckPropertyChecks {

  // Generators
  implicit val arbTag: Arbitrary[Tag] = Arbitrary(
    for {
      key <- Gen.alphaNumStr.suchThat(_.nonEmpty)
      value <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    } yield Tag.builder().key(key).value(value).build()
  )

  implicit val arbRole: Arbitrary[Role] = Arbitrary(
    for {
      accountId <- Gen.choose(100000000000L, 999999999999L)
      name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    } yield Role
      .builder()
      .arn(s"arn:aws:iam::$accountId:role/$name")
      .roleName(name)
      .build()
  )

  def genRoleArn: Gen[String] = for {
    accountId <- Gen.choose(100000000000L, 999999999999L)
    name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
  } yield s"arn:aws:iam::$accountId:role/$name"

  "groupIamRolesByTag" - {
    val account = AwsAccount("test-account", "arn:aws:iam::123456789012:root")
    val instant = Instant.now()
    val role = Role
      .builder()
      .arn("arn:aws:iam::12345678:role/name")
      .roleName("name")
      .build()

    "empty input handling" - {
      "should return empty map when given empty roleTags" in {
        val result = IamRoleInfo.groupIamRolesByTag(
          account,
          Map.empty,
          "provisionedRole",
          "friendlyName",
          "description",
          instant
        )

        result shouldBe empty
      }

      "should return empty map when no roles have grouping tag" in {
        val otherTag = Tag.builder().key("OtherTag").value("value").build()
        val roleTags = Map(role -> Set(otherTag))

        val result = IamRoleInfo.groupIamRolesByTag(
          account,
          roleTags,
          "provisionedRole",
          "friendlyName",
          "description",
          instant
        )

        result shouldBe empty
      }
    }

    "grouping behavior" - {
      "should group roles by the specified grouping tag" in {
        forAll(
          arbRole.arbitrary,
          arbRole.arbitrary,
          Gen.alphaNumStr.suchThat(_.nonEmpty),
          Gen.alphaNumStr.suchThat(_.nonEmpty)
        ) { (role1, role2, tagValue1, tagValue2) =>
          whenever(role1.arn != role2.arn && tagValue1 != tagValue2) {
            val groupTag1 =
              Tag.builder().key("provisionedRole").value(tagValue1).build()
            val groupTag2 =
              Tag.builder().key("provisionedRole").value(tagValue2).build()

            val roleTags = Map(
              role1 -> Set(groupTag1),
              role2 -> Set(groupTag2)
            )

            val result = IamRoleInfo.groupIamRolesByTag(
              account,
              roleTags,
              "provisionedRole",
              "friendlyName",
              "description",
              instant
            )

            result.keySet should contain allOf (tagValue1, tagValue2)
            result(tagValue1) should have size 1
            result(tagValue2) should have size 1
          }
        }
      }

      "should group multiple roles with same grouping tag value" in {
        forAll(
          arbRole.arbitrary,
          arbRole.arbitrary,
          Gen.alphaNumStr.suchThat(_.nonEmpty)
        ) { (role1, role2, provisionedRoleTag) =>
          whenever(role1.arn != role2.arn) {
            val groupTag = Tag
              .builder()
              .key("provisionedRole")
              .value(provisionedRoleTag)
              .build()

            val roleTags = Map(
              role1 -> Set(groupTag),
              role2 -> Set(groupTag)
            )

            val result = IamRoleInfo.groupIamRolesByTag(
              account,
              roleTags,
              "provisionedRole",
              "friendlyName",
              "description",
              instant
            )

            result should have size 1
            result(provisionedRoleTag) should have size 2
          }
        }
      }

      "should handle arbitrary number of roles in same group" in {
        forAll(
          Gen.listOfN(5, arbRole.arbitrary),
          Gen.alphaNumStr.suchThat(_.nonEmpty)
        ) { (roles, provisionedRoleTag) =>
          val uniqueRoles = roles.distinctBy(_.arn)
          whenever(uniqueRoles.size >= 2) {
            val groupTag = Tag
              .builder()
              .key("provisionedRole")
              .value(provisionedRoleTag)
              .build()
            val roleTags = uniqueRoles.map(role => role -> Set(groupTag)).toMap

            val result = IamRoleInfo.groupIamRolesByTag(
              account,
              roleTags,
              "provisionedRole",
              "friendlyName",
              "description",
              instant
            )

            result should have size 1
            result(provisionedRoleTag) should have size uniqueRoles.size
          }
        }
      }
    }

    "tag extraction" - {
      "should extract friendlyName from tags" in {
        forAll(
          arbRole.arbitrary,
          Gen.alphaNumStr.suchThat(_.nonEmpty),
          Gen.alphaNumStr.suchThat(_.nonEmpty)
        ) { (role, provisionedRoleTag, friendlyName) =>
          val groupTag = Tag
            .builder()
            .key("provisionedRole")
            .value(provisionedRoleTag)
            .build()
          val friendlyTag =
            Tag.builder().key("friendlyName").value(friendlyName).build()

          val roleTags = Map(role -> Set(groupTag, friendlyTag))

          val result = IamRoleInfo.groupIamRolesByTag(
            account,
            roleTags,
            "provisionedRole",
            "friendlyName",
            "description",
            instant
          )

          result(provisionedRoleTag).head.friendlyName shouldBe Some(
            friendlyName
          )
        }
      }

      "should extract description from tags" in {
        forAll(
          arbRole.arbitrary,
          Gen.alphaNumStr.suchThat(_.nonEmpty),
          Gen.alphaNumStr.suchThat(_.nonEmpty)
        ) { (role, provisionedRoleTag, description) =>
          val groupTag = Tag
            .builder()
            .key("provisionedRole")
            .value(provisionedRoleTag)
            .build()
          val descTag =
            Tag.builder().key("description").value(description).build()

          val roleTags = Map(role -> Set(groupTag, descTag))

          val result = IamRoleInfo.groupIamRolesByTag(
            account,
            roleTags,
            "provisionedRole",
            "friendlyName",
            "description",
            instant
          )

          result(provisionedRoleTag).head.description shouldBe Some(description)
        }
      }

      "should handle roles without friendlyName tag" in {
        forAll(
          arbRole.arbitrary,
          Gen.alphaNumStr.suchThat(_.nonEmpty)
        ) { (role, provisionedRoleTag) =>
          val groupTag = Tag
            .builder()
            .key("provisionedRole")
            .value(provisionedRoleTag)
            .build()
          val roleTags = Map(role -> Set(groupTag))

          val result = IamRoleInfo.groupIamRolesByTag(
            account,
            roleTags,
            "provisionedRole",
            "friendlyName",
            "description",
            instant
          )

          result(provisionedRoleTag).head.friendlyName shouldBe None
        }
      }

      "should handle roles without description tag" in {
        forAll(
          arbRole.arbitrary,
          Gen.alphaNumStr.suchThat(_.nonEmpty)
        ) { (role, provisionedRoleTag) =>
          val groupTag = Tag
            .builder()
            .key("provisionedRole")
            .value(provisionedRoleTag)
            .build()
          val roleTags = Map(role -> Set(groupTag))

          val result = IamRoleInfo.groupIamRolesByTag(
            account,
            roleTags,
            "provisionedRole",
            "friendlyName",
            "description",
            instant
          )

          result(provisionedRoleTag).head.description shouldBe None
        }
      }
    }
  }
}
