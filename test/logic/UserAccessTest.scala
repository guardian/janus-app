package logic

import com.gu.googleauth.UserIdentity
import com.gu.janus.model.*
import fixtures.Fixtures.*
import models.DeveloperPolicy
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inspectors, OptionValues}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import java.time.*

class UserAccessTest
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaCheckDrivenPropertyChecks {

  import UserAccess.*

  "userAccess" - {
    val testAccess =
      ACL(
        Map("test.user" -> ACLEntry(Set(fooDev, barDev), Set.empty)),
        Set(bazDev, quxDev)
      )

    "returns None if the user doesn't have any permissions" in {
      userAccess("username.does.not.exist", testAccess, Set.empty) should equal(
        None
      )
    }

    "returns the user's permissions if they exist" in {
      val permissions =
        userPermissions("test.user", testAccess, Set.empty)
      permissions should (contain(fooDev) and contain(barDev))
    }

    "include default permissions in all users' available permissions" in {
      val access =
        userPermissions("test.user", testAccess, Set.empty)
      testAccess.defaultPermissions foreach { perm =>
        access should contain(perm)
      }
    }

    "deduplicates a user's permissions" in {
      val permissions = Set(fooDev, barDev, fooDev, barDev)
      val acl =
        ACL(Map("test.user" -> ACLEntry(permissions, Set.empty)), Set.empty)
      val result = userPermissions("test.user", acl, Set.empty)
      result shouldEqual (permissions ++ acl.defaultPermissions)
    }

    "includes permissions derived from matching developer policies" in {
      val grant = DeveloperPolicyGrant("My Grant", "grant-id")
      val policy = DeveloperPolicy(
        "arn:aws:iam::123:policy/developer-policy/grant-id/p1",
        "p1",
        "grant-id",
        None,
        fooAct
      )
      val acl = ACL(
        Map("test.user" -> ACLEntry(Set(fooDev), Set(grant))),
        Set.empty
      )
      val permissions =
        userPermissions("test.user", acl, Set(policy))
      val policyPermission = DeveloperPolicies.toPermission(policy)
      permissions should contain(policyPermission)
    }

    "a matching policyGrant results in additional permissions beyond the base ACL permissions" in {
      val grant = DeveloperPolicyGrant("My Grant", "grant-id")
      val policy = DeveloperPolicy(
        "arn:aws:iam::123:policy/developer-policy/grant-id/p1",
        "p1",
        "grant-id",
        None,
        fooAct
      )
      val acl = ACL(
        Map("test.user" -> ACLEntry(Set(fooDev), Set(grant))),
        Set.empty
      )
      val policyPermission = DeveloperPolicies.toPermission(policy)
      val permissionsWithPolicy =
        userPermissions("test.user", acl, Set(policy))
      val permissionsWithoutPolicy =
        userPermissions("test.user", acl, Set.empty)
      // Base ACL permissions are preserved in the result
      permissionsWithPolicy should contain(fooDev)
      permissionsWithoutPolicy should contain(fooDev)
      // The only addition is exactly the policy-derived permission
      (permissionsWithPolicy -- permissionsWithoutPolicy) shouldEqual Set(
        policyPermission
      )
    }

    "does not include developer policies whose grant ID does not match any ACL entry grant" in {
      val grant = DeveloperPolicyGrant("My Grant", "grant-id")
      val unmatchedPolicy = DeveloperPolicy(
        "arn:aws:iam::123:policy/developer-policy/other-id/p1",
        "p1",
        "other-id",
        None,
        fooAct
      )
      val acl = ACL(
        Map("test.user" -> ACLEntry(Set(fooDev), Set(grant))),
        Set.empty
      )
      val permissions =
        userPermissions("test.user", acl, Set(unmatchedPolicy))
      val unmatchedPermission = DeveloperPolicies.toPermission(unmatchedPolicy)
      permissions should not contain unmatchedPermission
    }

    "does not include developer policies when the user has no policy grants" in {
      val policy = DeveloperPolicy(
        "arn:aws:iam::123:policy/developer-policy/grant-id/p1",
        "p1",
        "grant-id",
        None,
        fooAct
      )
      val acl = ACL(
        Map("test.user" -> ACLEntry(Set(fooDev), Set.empty)),
        Set.empty
      )
      val permissions =
        userPermissions("test.user", acl, Set(policy))
      permissions shouldEqual Set(fooDev)
    }

    "groups permissions by account" in {
      val acl = ACL(
        Map("test.user" -> ACLEntry(Set(fooDev, barDev), Set.empty)),
        Set.empty
      )
      val result = userAccess("test.user", acl, Set.empty).value
      result(fooAct).permissions should contain(fooDev)
      result(barAct).permissions should contain(barDev)
    }

    "groups developer policies by account separately from permissions" in {
      val grant = DeveloperPolicyGrant("My Grant", "grant-id")
      val policy = DeveloperPolicy(
        "arn:aws:iam::123:policy/developer-policy/grant-id/p1",
        "p1",
        "grant-id",
        None,
        fooAct
      )
      val acl = ACL(
        Map("test.user" -> ACLEntry(Set(fooDev), Set(grant))),
        Set.empty
      )
      val result = userAccess("test.user", acl, Set(policy)).value
      result(fooAct).permissions should contain(fooDev)
      result(fooAct).developerPolicies should contain(policy)
    }

    "property: result only contains developer policy permissions for grants the user holds" in {
      def generateDevPolicyAndGrant(
          id: Int
      ): (DeveloperPolicy, DeveloperPolicyGrant) = {
        val grantId = s"grant-$id"
        (
          DeveloperPolicy(
            s"arn:aws:iam::123:policy/developer-policy/$grantId/p$id",
            s"p$id",
            grantId,
            None,
            fooAct
          ),
          DeveloperPolicyGrant(s"Grant $id", grantId)
        )
      }

      val (allDeveloperPolicies, allDeveloperPolicyGrants) =
        (for (i <- 1 to 10) yield generateDevPolicyAndGrant(i)).unzip

      forAll(
        Gen.someOf(allDeveloperPolicyGrants),
        Gen.someOf(allDeveloperPolicies)
      ) { (developerPolicyGrantsForUser, availablePolicies) =>
        val acl = ACL(
          Map(
            "test.user" -> ACLEntry(
              Set.empty,
              developerPolicyGrantsForUser.toSet
            )
          ),
          Set.empty
        )

        val resultPerms =
          userPermissions("test.user", acl, availablePolicies.toSet)

        // The expected permissions are those derived from available policies
        // whose grant is held by the user
        val expected = availablePolicies
          .filter(p =>
            developerPolicyGrantsForUser.exists(_.id == p.policyGrantId)
          )
          .map(DeveloperPolicies.toPermission)
          .toSet

        resultPerms.intersect(
          availablePolicies
            .map(DeveloperPolicies.toPermission)
            .toSet
        ) shouldEqual expected
      }
    }
  }

  "hasAccess" - {
    val adminACL = ACL(
      Map("test.user" -> ACLEntry(Set(fooDev, barDev), Set.empty)),
      allTestPerms
    )

    "returns true when given a user that has an entry" in {
      hasAccess("test.user", adminACL) shouldEqual true
    }

    "returns false if the user is not explicitly mentioned" in {
      hasAccess("not.in.the.list", adminACL) shouldEqual false
    }
  }

  "policyGrantsForUser" - {
    val grant1 = DeveloperPolicyGrant("Grant 1", "grant-1")
    val grant2 = DeveloperPolicyGrant("Grant 2", "grant-2")
    val acl = ACL(
      Map(
        "user.with.grants" -> ACLEntry(Set(fooDev), Set(grant1, grant2)),
        "user.with.one.grant" -> ACLEntry(Set(fooDev), Set(grant1)),
        "user.with.no.grants" -> ACLEntry(Set(fooDev), Set.empty)
      ),
      Set.empty
    )

    "returns the set of grants for a user that has them" in {
      policyGrantsForUser("user.with.grants", acl) shouldEqual Set(
        grant1,
        grant2
      )
    }

    "returns an empty set for a user that has no grants" in {
      policyGrantsForUser("user.with.no.grants", acl) shouldEqual Set.empty
    }
  }

  "checkUserPermissionWithSource" - {
    val acl = ACL(
      Map(
        "user" -> ACLEntry(Set(fooDev), Set.empty)
      ),
      Set.empty
    )
    val adminAcl = ACL(Map("admin" -> ACLEntry(allTestPerms, Set.empty)))
    val supportAcl = SupportACL.create(
      Map(
        Instant.now().minus(Duration.ofDays(1)) -> (
          "support.user",
          "another.support.user"
        )
      ),
      allTestPerms
    )

    "returns the permission if a user has been granted access" in {
      val (permission, _) = checkUserPermissionWithSource(
        "user",
        fooDev.id,
        Instant.now(),
        acl,
        adminAcl,
        supportAcl,
        Set.empty
      ).value
      permission shouldEqual fooDev
    }

    "returns the permission if it has been granted via admin access" in {
      Inspectors.forAll(allTestPerms) { adminPermission =>
        val (permission, _) = checkUserPermissionWithSource(
          "admin",
          adminPermission.id,
          Instant.now(),
          acl,
          adminAcl,
          supportAcl,
          Set.empty
        ).value
        permission shouldEqual adminPermission
      }
    }

    "returns the permission if it has been granted via support access" in {
      Inspectors.forAll(supportAcl.supportAccess) { supportPermission =>
        val (permission, _) = checkUserPermissionWithSource(
          "support.user",
          supportPermission.id,
          Instant.now(),
          acl,
          adminAcl,
          supportAcl,
          Set.empty
        ).value
        permission shouldEqual supportPermission
      }
    }

    "returns None if the permission has not been granted to the user" in {
      checkUserPermissionWithSource(
        "no.permissions",
        fooDev.id,
        Instant.now(),
        acl,
        adminAcl,
        supportAcl,
        Set.empty
      ) shouldBe None
    }

    "explicit access flag" - {
      val explicitAcl = ACL(
        Map("user" -> ACLEntry(Set(fooDev), Set.empty)),
        Set.empty
      )
      val adminAcl = ACL(Map("admin" -> ACLEntry(Set(fooDev), Set.empty)))
      val supportAcl = SupportACL.create(
        Map(
          Instant.now().minus(Duration.ofDays(1)) -> (
            "support.user",
            "another.support.user"
          )
        ),
        Set(fooDev)
      )

      "is true when the permission was granted via the explicit ACL" in {
        val (_, explicitAccess) = checkUserPermissionWithSource(
          "user",
          fooDev.id,
          Instant.now(),
          explicitAcl,
          adminAcl,
          supportAcl,
          Set.empty
        ).value
        explicitAccess shouldEqual true
      }

      "is false when the permission was granted via admin access only" in {
        val (_, explicitAccess) = checkUserPermissionWithSource(
          "admin",
          fooDev.id,
          Instant.now(),
          explicitAcl,
          adminAcl,
          supportAcl,
          Set.empty
        ).value
        explicitAccess shouldEqual false
      }

      "is false when the permission was granted via support access only" in {
        val (_, explicitAccess) = checkUserPermissionWithSource(
          "support.user",
          fooDev.id,
          Instant.now(),
          explicitAcl,
          adminAcl,
          supportAcl,
          Set.empty
        ).value
        explicitAccess shouldEqual false
      }
    }
  }

  "username" - {
    "uses the email address, not first name and last name (which doesn't work for i18n names)" in {
      username(
        UserIdentity(
          "sub",
          "first.last@example.com",
          "First",
          "Last One Two",
          86400,
          None
        )
      ) shouldEqual "first.last"
    }

    "lower-cases the provided email address" in {
      username(
        UserIdentity(
          "sub",
          "First.Last@example.com",
          "First",
          "Last One Two",
          86400,
          None
        )
      ) shouldEqual "first.last"
    }
  }
}
