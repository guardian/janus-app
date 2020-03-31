package com.gu.janus

import com.gu.janus.model._
import org.joda.time.{DateTime, Period}
import org.scalatest.{FreeSpec, Matchers}


class ValidationTest extends FreeSpec with Matchers {
  val account1 = AwsAccount("Test 1", "test1")
  val account2 = AwsAccount("Test 2", "test2")
  val emptyAcl = ACL(Map.empty, Set.empty)
  val emptySupportAcl = SupportACL.create(Map.empty, Set.empty, Period.seconds(100))

  "policySizeChecks" - {
    val smallPermission = Permission(account1, "perm1", "Test valid permission", "xxx", false)
    val largePermission = Permission(account1, "perm2", "Test large permission", "x" * 2000, false)

    "returns nothing if the provided data contains no large policies" in {
      val janusData = JanusData(
        Set(account1), ACL(Map("user1" -> Set(smallPermission))), emptyAcl, emptySupportAcl
      )
      Validation.policySizeChecks(janusData).valid shouldEqual true
    }

    "returns a warning if there is a large policy" - {
      "in the access ACL" in {
        val janusData = JanusData(
          Set(account1),
          access = ACL(Map("user1" -> Set(largePermission))),
          emptyAcl, emptySupportAcl
        )
        Validation.policySizeChecks(janusData).warnings should not be empty
      }

      "in the admin ACL" in {
        val janusData = JanusData(
          Set(account1), emptyAcl,
          admin = ACL(Map("user1" -> Set(largePermission))),
          emptySupportAcl
        )
        Validation.policySizeChecks(janusData).warnings should not be empty
      }

      "in the support ACL" in {
        val janusData = JanusData(
          Set(account1), emptyAcl, emptyAcl,
          support = SupportACL.create(Map.empty, Set(largePermission), Period.seconds(100))
        )
        Validation.policySizeChecks(janusData).warnings should not be empty
      }

      "and returns an 'invalid' validation result if a warning is generated" in {
        val janusData = JanusData(
          Set(account1),
          access = ACL(Map("user1" -> Set(largePermission))),
          emptyAcl, emptySupportAcl
        )
        Validation.policySizeChecks(janusData).valid shouldEqual false
      }
    }
  }

  "permissionIdUniqueness" - {
    "returns nothing for valid permissions" in {
      val permission1 = Permission(account1, "perm1", "Test valid permission", "xxx", false)
      val permission2 = Permission(account1, "perm2", "Test large permission", "x" * 2000, false)
      val janusData = JanusData(
        Set(account1), ACL(Map("user1" -> Set(permission1, permission2))), emptyAcl, emptySupportAcl
      )
      Validation.permissionUniqueness(janusData).valid shouldEqual true
    }

    "returns nothing for duplicate permissions in separate accounts" in {
      val permission1 = Permission(account1, "perm1", "Test valid permission", "xxx", false)
      val permission2 = Permission(account2, "perm1", "Test valid permission", "xxx", false)
      val janusData = JanusData(
        Set(account1), ACL(Map("user1" -> Set(permission1, permission2))), emptyAcl, emptySupportAcl
      )
      Validation.permissionUniqueness(janusData).valid shouldEqual true
    }

    "returns a validation error for permissions with duplicate IDs (concatenation of account & label)" in {
      val permission1 = Permission(account1, "perm1", "Test valid permission", "xxx", false)
      val permission2 = Permission(account1, "perm1", "Another valid permission", "yyy", true)
      val janusData = JanusData(
        Set(account1), ACL(Map("user1" -> Set(permission1, permission2))), emptyAcl, emptySupportAcl
      )
      Validation.permissionUniqueness(janusData).errors should not be empty
    }

    "returns a validation error for duplicate permissions across multiple users" in {
      val permission1 = Permission(account1, "perm1", "Test valid permission", "xxx", false)
      val permission2 = Permission(account1, "perm1", "Another valid permission", "yyy", true)
      val janusData = JanusData(
        Set(account1), ACL(Map("user1" -> Set(permission1), "user2" -> Set(permission2))), emptyAcl, emptySupportAcl
      )
      Validation.permissionUniqueness(janusData).errors should not be empty
    }
  }
}
