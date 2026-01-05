package data

import com.gu.janus.model.AwsAccount
import models.IamRoleInfo
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.time.Instant

class ProvisionedRoleCacheTest
    extends AnyFreeSpec
    with Matchers
    with ScalaCheckPropertyChecks {

  // Generators
  val accountGen: Gen[AwsAccount] = for {
    accountId <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    authConfigKey <- Gen.alphaNumStr.suchThat(_.nonEmpty)
  } yield AwsAccount(accountId, authConfigKey)

  val iamRoleInfoGen: Gen[IamRoleInfo] = for {
    roleArn <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    account <- accountGen
    friendlyName <- Gen.option(Gen.alphaNumStr)
    description <- Gen.option(Gen.alphaNumStr)
  } yield IamRoleInfo(
    roleArn,
    account,
    friendlyName,
    description,
    Instant.now()
  )

  val tagValueGen: Gen[String] = Gen.alphaNumStr.suchThat(_.nonEmpty)

  val roleListGen: Gen[List[IamRoleInfo]] = Gen.listOf(iamRoleInfoGen)

  "ProvisionedRoleCache" - {

    "findByTag" - {
      "should return empty list when tag does not exist" in {
        val cache = new ProvisionedRoleCache()
        forAll(tagValueGen) { tagValue =>
          cache.get(tagValue) shouldBe List.empty[IamRoleInfo]
        }
      }

      "should return roles for existing tag" in {
        forAll(tagValueGen, accountGen, roleListGen) {
          (tagValue, account, roles) =>
            val cache = new ProvisionedRoleCache()
            cache.update(tagValue, account, roles)
            cache.get(tagValue) should contain theSameElementsAs roles
        }
      }

      "should return combined roles from multiple accounts for same tag" in {
        forAll(tagValueGen, accountGen, accountGen, roleListGen, roleListGen) {
          (tagValue, account1, account2, roles1, roles2) =>
            whenever(account1 != account2) {
              val cache = new ProvisionedRoleCache()
              cache.update(tagValue, account1, roles1)
              cache.update(tagValue, account2, roles2)
              val result = cache.get(tagValue)
              result should contain allElementsOf roles1
              result should contain allElementsOf roles2
            }
        }
      }

      "should handle empty role lists" in {
        forAll(tagValueGen, accountGen) { (tagValue, account) =>
          val cache = new ProvisionedRoleCache()
          cache.update(tagValue, account, Nil)
          cache.get(tagValue) shouldBe List.empty[IamRoleInfo]
        }
      }
    }

    "update" - {
      "should store roles for a tag and account" in {
        forAll(tagValueGen, accountGen, roleListGen) {
          (tagValue, account, roles) =>
            val cache = new ProvisionedRoleCache()
            cache.update(tagValue, account, roles)
            cache.get(tagValue) should contain theSameElementsAs roles
        }
      }

      "should overwrite existing roles for same tag and account" in {
        forAll(tagValueGen, accountGen, roleListGen, roleListGen) {
          (tagValue, account, roles1, roles2) =>
            val cache = new ProvisionedRoleCache()
            cache.update(tagValue, account, roles1)
            cache.update(tagValue, account, roles2)
            cache.get(tagValue) should contain theSameElementsAs roles2
        }
      }

      "should maintain roles from different accounts separately" in {
        forAll(tagValueGen, accountGen, accountGen, roleListGen, roleListGen) {
          (tagValue, account1, account2, roles1, roles2) =>
            whenever(account1 != account2) {
              val cache = new ProvisionedRoleCache()
              cache.update(tagValue, account1, roles1)
              cache.update(tagValue, account2, roles2)
              cache.update(tagValue, account1, Nil)
              val result = cache.get(tagValue)
              result should contain theSameElementsAs roles2
              result should not contain allElementsOf(roles1)
            }
        }
      }

      "should handle multiple tags for same account" in {
        forAll(tagValueGen, tagValueGen, accountGen, roleListGen, roleListGen) {
          (tag1, tag2, account, roles1, roles2) =>
            whenever(tag1 != tag2) {
              val cache = new ProvisionedRoleCache()
              cache.update(tag1, account, roles1)
              cache.update(tag2, account, roles2)
              cache.get(tag1) should contain theSameElementsAs roles1
              cache.get(tag2) should contain theSameElementsAs roles2
            }
        }
      }

      "should preserve existing entries when updating different tag" in {
        forAll(tagValueGen, tagValueGen, accountGen, roleListGen, roleListGen) {
          (tag1, tag2, account, roles1, roles2) =>
            whenever(tag1 != tag2) {
              val cache = new ProvisionedRoleCache()
              cache.update(tag1, account, roles1)
              cache.update(tag2, account, roles2)
              cache.get(tag1) should contain theSameElementsAs roles1
            }
        }
      }
    }

    "getAll" - {
      "should return empty map when cache is empty" in {
        val cache = new ProvisionedRoleCache()
        cache.getAll shouldBe Map.empty[String, List[IamRoleInfo]]
      }

      "should return all tags with their combined roles" in {
        forAll(tagValueGen, tagValueGen, accountGen, roleListGen, roleListGen) {
          (tag1, tag2, account, roles1, roles2) =>
            whenever(tag1 != tag2) {
              val cache = new ProvisionedRoleCache()
              cache.update(tag1, account, roles1)
              cache.update(tag2, account, roles2)
              val result = cache.getAll
              result.keys should contain allOf (tag1, tag2)
              result(tag1) should contain theSameElementsAs roles1
              result(tag2) should contain theSameElementsAs roles2
            }
        }
      }

      "should flatten roles from multiple accounts per tag" in {
        forAll(tagValueGen, accountGen, accountGen, roleListGen, roleListGen) {
          (tagValue, account1, account2, roles1, roles2) =>
            whenever(account1 != account2) {
              val cache = new ProvisionedRoleCache()
              cache.update(tagValue, account1, roles1)
              cache.update(tagValue, account2, roles2)
              val result = cache.getAll
              result(tagValue) should contain allElementsOf roles1
              result(tagValue) should contain allElementsOf roles2
            }
        }
      }

      "should handle tags with empty role lists" in {
        forAll(tagValueGen, accountGen) { (tagValue, account) =>
          val cache = new ProvisionedRoleCache()
          cache.update(tagValue, account, Nil)
          val result = cache.getAll
          result.get(tagValue).foreach(_ shouldBe List.empty[IamRoleInfo])
        }
      }

      "should return consistent results across multiple calls" in {
        forAll(tagValueGen, accountGen, roleListGen) {
          (tagValue, account, roles) =>
            val cache = new ProvisionedRoleCache()
            cache.update(tagValue, account, roles)
            val result1 = cache.getAll
            val result2 = cache.getAll
            result1 should equal(result2)
        }
      }
    }

    "cache behaviour" - {
      "should handle multiple updates without data loss" in {
        forAll(Gen.listOfN(10, Gen.zip(tagValueGen, accountGen, roleListGen))) {
          updates =>
            val cache = new ProvisionedRoleCache()
            updates.foreach { case (tag, account, roles) =>
              cache.update(tag, account, roles)
            }
            cache.getAll.size should be <= updates
              .map((tagValue, _, _) => tagValue)
              .distinct
              .size
        }
      }

      "should maintain data integrity after mixed operations" in {
        forAll(tagValueGen, accountGen, accountGen, roleListGen, roleListGen) {
          (tagValue, account1, account2, roles1, roles2) =>
            whenever(
              account1 != account2 && roles1.nonEmpty && roles2.nonEmpty
            ) {
              val cache = new ProvisionedRoleCache()
              cache.update(tagValue, account1, roles1)
              cache.get(tagValue) should contain theSameElementsAs roles1

              cache.update(tagValue, account2, roles2)
              val afterSecondUpdate = cache.get(tagValue)
              afterSecondUpdate should contain allElementsOf roles1
              afterSecondUpdate should contain allElementsOf roles2

              cache.getAll(
                tagValue
              ) should contain allElementsOf afterSecondUpdate
            }
        }
      }
    }
  }
}
