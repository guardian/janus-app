package com.gu.janus

import com.gu.janus.Validation.{isClean, noErrors}
import com.gu.janus.model._
import com.gu.janus.policy.Iam.Effect.Allow
import com.gu.janus.policy.Iam.{Action, Policy, Resource, Statement}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.Duration

class ValidationTest extends AnyFreeSpec with Matchers {
  val account1 = AwsAccount("Test 1", "test1")
  val account2 = AwsAccount("Test 2", "test2")
  val emptyAcl = ACL(Map.empty, Map.empty, Set.empty)
  val emptySupportAcl =
    SupportACL.create(Map.empty, Set.empty)
  val simpleStatement = Statement(
    Allow,
    Seq(Action("sts:GetCallerIdentity")),
    Seq(Resource("*"))
  )
  val simplePolicy = Policy(Seq(simpleStatement))
  val largeInlinePolicy = Policy(
    Seq.fill(200)(simpleStatement)
  )

  "policySizeChecks" - {
    val smallPermission =
      Permission(
        account1,
        "perm1",
        "Test valid permission",
        simplePolicy,
        false
      )
    val largePermission =
      Permission(
        account1,
        "perm2",
        "Test large permission",
        largeInlinePolicy,
        false
      )
    val smallPermissionWithLargeManagedPolicyArns =
      Permission.withManagedPolicyArns(
        account1,
        "perm1",
        "Test valid permission",
        simplePolicy,
        List.fill(200)("arn:aws:iam::aws:policy/ReadOnlyAccess"),
        false
      )

    "returns nothing if the provided data contains no large policies" in {
      val janusData = JanusData(
        Set(account1),
        ACL(Map("user1" -> Set(smallPermission)), Map.empty),
        emptyAcl,
        emptySupportAcl,
        None
      )
      isClean(Validation.policySizeChecks(janusData)) shouldEqual true
    }

    "returns a warning if there is a large policy" - {
      "in the access ACL" - {
        "for a large inline policy" in {
          val janusData = JanusData(
            Set(account1),
            access = ACL(Map("user1" -> Set(largePermission)), Map.empty),
            emptyAcl,
            emptySupportAcl,
            None
          )
          Validation.policySizeChecks(janusData).warnings should not be empty
        }

        "for large managedPolicyArns" in {
          val janusData = JanusData(
            Set(account1),
            access = ACL(
              Map("user1" -> Set(smallPermissionWithLargeManagedPolicyArns)),
              Map.empty
            ),
            emptyAcl,
            emptySupportAcl,
            None
          )
          Validation.policySizeChecks(janusData).warnings should not be empty
        }
      }

      "in the admin ACL" - {
        "for a large inline policy" in {
          val janusData = JanusData(
            Set(account1),
            emptyAcl,
            admin = ACL(Map("user1" -> Set(largePermission)), Map.empty),
            emptySupportAcl,
            None
          )
          Validation.policySizeChecks(janusData).warnings should not be empty
        }

        "for large managedPolicyArns" in {
          val janusData = JanusData(
            Set(account1),
            emptyAcl,
            admin = ACL(
              Map("user1" -> Set(smallPermissionWithLargeManagedPolicyArns)),
              Map.empty
            ),
            emptySupportAcl,
            None
          )
          Validation.policySizeChecks(janusData).warnings should not be empty
        }
      }

      "in the support ACL" - {
        "for a large inline policy" in {
          val janusData = JanusData(
            Set(account1),
            emptyAcl,
            emptyAcl,
            support = SupportACL
              .create(Map.empty, Set(largePermission)),
            None
          )
          Validation.policySizeChecks(janusData).warnings should not be empty
        }

        "for large managedPolicyArns" in {
          val janusData = JanusData(
            Set(account1),
            emptyAcl,
            emptyAcl,
            support = SupportACL.create(
              Map.empty,
              Set(smallPermissionWithLargeManagedPolicyArns)
            ),
            None
          )
          Validation.policySizeChecks(janusData).warnings should not be empty
        }
      }

      "returns an 'invalid' validation result if a warning is generated" in {
        val janusData = JanusData(
          Set(account1),
          access = ACL(Map("user1" -> Set(largePermission)), Map.empty),
          emptyAcl,
          emptySupportAcl,
          None
        )
        isClean(Validation.policySizeChecks(janusData)) shouldEqual false
      }
    }
  }

  "permissionIdUniqueness" - {
    "returns nothing for valid permissions" in {
      val permission1 =
        Permission(
          account1,
          "perm1",
          "Test valid permission",
          simplePolicy,
          false
        )
      val permission2 = Permission(
        account1,
        "perm2",
        "Test large permission",
        largeInlinePolicy,
        false
      )
      val janusData = JanusData(
        Set(account1),
        ACL(Map("user1" -> Set(permission1, permission2)), Map.empty),
        emptyAcl,
        emptySupportAcl,
        None
      )
      isClean(Validation.permissionUniqueness(janusData)) shouldEqual true
    }

    "returns nothing for duplicate permissions in separate accounts" in {
      val permission1 =
        Permission(
          account1,
          "perm1",
          "Test valid permission",
          simplePolicy,
          false
        )
      val permission2 =
        Permission(
          account2,
          "perm1",
          "Test valid permission",
          simplePolicy,
          false
        )
      val janusData = JanusData(
        Set(account1),
        ACL(Map("user1" -> Set(permission1, permission2)), Map.empty),
        emptyAcl,
        emptySupportAcl,
        None
      )
      isClean(Validation.permissionUniqueness(janusData)) shouldEqual true
    }

    "returns a validation error for permissions with duplicate IDs (concatenation of account & label)" in {
      val permission1 =
        Permission(
          account1,
          "perm1",
          "Test valid permission",
          simplePolicy,
          false
        )
      val permission2 =
        Permission(
          account1,
          "perm1",
          "Another valid permission",
          simplePolicy,
          true
        )
      val janusData = JanusData(
        Set(account1),
        ACL(Map("user1" -> Set(permission1, permission2)), Map.empty),
        emptyAcl,
        emptySupportAcl,
        None
      )
      Validation.permissionUniqueness(janusData).errors should not be empty
    }

    "returns a validation error for duplicate permissions across multiple users" in {
      val permission1 =
        Permission(
          account1,
          "perm1",
          "Test valid permission",
          simplePolicy,
          false
        )
      val permission2 =
        Permission(
          account1,
          "perm1",
          "Another valid permission",
          simplePolicy,
          true
        )
      val janusData = JanusData(
        Set(account1),
        ACL(
          Map("user1" -> Set(permission1), "user2" -> Set(permission2)),
          Map.empty
        ),
        emptyAcl,
        emptySupportAcl,
        None
      )
      Validation.permissionUniqueness(janusData).errors should not be empty
    }
  }

  "isClean" - {
    "returns true for a result with no warnings or errors" in {
      val result = ValidationResult(Nil, Nil)
      isClean(result) shouldEqual true
    }

    "returns false for a result with errors but no warnings" in {
      val result = ValidationResult(errors = List("error"), Nil)
      isClean(result) shouldEqual false
    }

    "returns false for a result with warnings but no errors" in {
      val result = ValidationResult(Nil, warnings = List("warning"))
      isClean(result) shouldEqual false
    }

    "returns false for a result with warnings and errors" in {
      val result =
        ValidationResult(errors = List("error"), warnings = List("warning"))
      isClean(result) shouldEqual false
    }
  }

  "noErrors" - {
    "returns true for a result with no warnings or errors" in {
      val result = ValidationResult(Nil, Nil)
      noErrors(result) shouldEqual true
    }

    "returns true for a result with warnings but no errors" in {
      val result = ValidationResult(Nil, warnings = List("warning"))
      noErrors(result) shouldEqual true
    }

    "returns false for a result with errors but no warnings" in {
      val result = ValidationResult(errors = List("error"), Nil)
      noErrors(result) shouldEqual false
    }

    "returns false for a result with warnings and errors" in {
      val result =
        ValidationResult(errors = List("error"), warnings = List("warning"))
      noErrors(result) shouldEqual false
    }
  }
}
