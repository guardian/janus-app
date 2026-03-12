package logic

import com.gu.googleauth.UserIdentity
import com.gu.janus.model.*
import fixtures.Fixtures.*
import models.{AccessSource, DeveloperPolicy}
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

  "internalUserAccess" - {
    val testAccess =
      ACL(
        Map("test.user" -> ACLEntry(Set(fooDev, barDev), Set.empty)),
        Set(bazDev, quxDev)
      )

    "returns None if the user doesn't have any permissions" in {
      internalUserAccess(
        "username.does.not.exist",
        JanusData(
          Set.empty,
          testAccess,
          ACL(Map.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set.empty
      ) should equal(
        None
      )
    }

    "returns the user's permissions if they exist" in {
      val result = internalUserAccess(
        "test.user",
        JanusData(
          Set.empty,
          testAccess,
          ACL(Map.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set.empty
      ).value
      result.values.flatMap(_.permissions) should (contain(fooDev) and contain(
        barDev
      ))
    }

    "includes default permissions in all users' available permissions" in {
      val result = internalUserAccess(
        "test.user",
        JanusData(
          Set.empty,
          testAccess,
          ACL(Map.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set.empty
      ).value
      testAccess.defaultPermissions foreach { perm =>
        result.values.flatMap(_.permissions) should contain(perm)
      }
    }

    "deduplicates a user's permissions" in {
      val permissions = Set(fooDev, barDev, fooDev, barDev)
      val acl =
        ACL(Map("test.user" -> ACLEntry(permissions, Set.empty)), Set.empty)
      val result = internalUserAccess(
        "test.user",
        JanusData(
          Set.empty,
          acl,
          ACL(Map.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set.empty
      ).value
      result.values
        .flatMap(_.permissions)
        .toSet shouldEqual (permissions ++ acl.defaultPermissions)
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
      val result = internalUserAccess(
        "test.user",
        JanusData(
          Set.empty,
          acl,
          ACL(Map.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set(policy)
      ).value
      result(fooAct).developerPolicies should contain(policy)
    }

    "a matching policyGrant results in additional developer policies beyond the base ACL permissions" in {
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
      val resultWithPolicy = internalUserAccess(
        "test.user",
        JanusData(
          Set.empty,
          acl,
          ACL(Map.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set(policy)
      ).value
      val resultWithoutPolicy = internalUserAccess(
        "test.user",
        JanusData(
          Set.empty,
          acl,
          ACL(Map.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set.empty
      ).value
      resultWithPolicy(fooAct).permissions should contain(fooDev)
      resultWithoutPolicy(fooAct).permissions should contain(fooDev)
      resultWithPolicy(fooAct).developerPolicies should contain(policy)
      resultWithoutPolicy(fooAct).developerPolicies shouldEqual Nil
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
      val result = internalUserAccess(
        "test.user",
        JanusData(
          Set.empty,
          acl,
          ACL(Map.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set(unmatchedPolicy)
      ).value
      result(fooAct).developerPolicies should not contain unmatchedPolicy
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
      val result = internalUserAccess(
        "test.user",
        JanusData(
          Set.empty,
          acl,
          ACL(Map.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set(policy)
      ).value
      result(fooAct).permissions shouldEqual List(fooDev)
      result(fooAct).developerPolicies shouldEqual Nil
    }

    "groups permissions by account" in {
      val acl = ACL(
        Map("test.user" -> ACLEntry(Set(fooDev, barDev), Set.empty)),
        Set.empty
      )
      val result = internalUserAccess(
        "test.user",
        JanusData(
          Set.empty,
          acl,
          ACL(Map.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set.empty
      ).value
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
      val result = internalUserAccess(
        "test.user",
        JanusData(
          Set.empty,
          acl,
          ACL(Map.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set(policy)
      ).value
      result(fooAct).permissions should contain(fooDev)
      result(fooAct).developerPolicies should contain(policy)
    }

    "does not return results from the admin ACL" in {
      val adminOnlyUser = "admin.only"
      val janusData = JanusData(
        accounts = Set.empty,
        access = ACL(Map.empty),
        admin = ACL(Map(adminOnlyUser -> ACLEntry(Set(fooDev), Set.empty))),
        support = SupportACL.create(Map.empty, Set.empty),
        permissionsRepo = None
      )
      internalUserAccess(adminOnlyUser, janusData, Set.empty) shouldEqual None
    }

    "property: result only contains developer policies for grants the user holds" in {
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

        val resultPolicies =
          internalUserAccess(
            "test.user",
            JanusData(
              Set.empty,
              acl,
              ACL(Map.empty),
              SupportACL.create(Map.empty, Set.empty),
              None
            ),
            availablePolicies.toSet
          )
            .map(_.values.flatMap(_.developerPolicies).toSet)
            .getOrElse(Set.empty)

        val expected = availablePolicies
          .filter(p =>
            developerPolicyGrantsForUser.exists(_.id == p.policyGrantId)
          )
          .toSet

        resultPolicies shouldEqual expected
      }
    }
  }

  "adminUserAccess" - {
    val testAccess =
      ACL(
        Map("test.user" -> ACLEntry(Set(fooDev, barDev), Set.empty)),
        Set(bazDev, quxDev)
      )

    "returns None if the user doesn't have any permissions" in {
      adminUserAccess(
        "username.does.not.exist",
        JanusData(
          Set.empty,
          ACL(Map.empty),
          testAccess,
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set.empty
      ) should equal(
        None
      )
    }

    "returns the user's permissions if they exist" in {
      val result = adminUserAccess(
        "test.user",
        JanusData(
          Set.empty,
          ACL(Map.empty),
          testAccess,
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set.empty
      ).value
      result.values.flatMap(_.permissions) should (contain(fooDev) and contain(
        barDev
      ))
    }

    "includes default permissions in all users' available permissions" in {
      val result = adminUserAccess(
        "test.user",
        JanusData(
          Set.empty,
          ACL(Map.empty),
          testAccess,
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set.empty
      ).value
      testAccess.defaultPermissions foreach { perm =>
        result.values.flatMap(_.permissions) should contain(perm)
      }
    }

    "groups permissions by account" in {
      val acl = ACL(
        Map("test.user" -> ACLEntry(Set(fooDev, barDev), Set.empty)),
        Set.empty
      )
      val result = adminUserAccess(
        "test.user",
        JanusData(
          Set.empty,
          ACL(Map.empty),
          acl,
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set.empty
      ).value
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
      val result = adminUserAccess(
        "test.user",
        JanusData(
          Set.empty,
          ACL(Map.empty),
          acl,
          SupportACL.create(Map.empty, Set.empty),
          None
        ),
        Set(policy)
      ).value
      result(fooAct).permissions should contain(fooDev)
      result(fooAct).developerPolicies should contain(policy)
    }

    "does not return results from the internal ACL" in {
      val internalOnlyUser = "internal.only"
      val janusData = JanusData(
        accounts = Set.empty,
        access = ACL(Map(internalOnlyUser -> ACLEntry(Set(fooDev), Set.empty))),
        admin = ACL(Map("admin.user" -> ACLEntry(Set(barDev), Set.empty))),
        support = SupportACL.create(Map.empty, Set.empty),
        permissionsRepo = None
      )
      adminUserAccess(internalOnlyUser, janusData, Set.empty) shouldEqual None
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
    val janusData = JanusData(
      accounts = Set.empty,
      access = acl,
      admin = adminAcl,
      support = supportAcl,
      permissionsRepo = None
    )

    "returns the permission if a user has been granted access" in {
      val (permission, _) = checkUserPermissionWithSource(
        "user",
        fooDev.id,
        Instant.now(),
        janusData,
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
          janusData,
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
          janusData,
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
        janusData,
        Set.empty
      ) shouldBe None
    }

    "access source" - {
      val internalAcl = ACL(
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
      val janusData = JanusData(
        accounts = Set.empty,
        access = internalAcl,
        admin = adminAcl,
        support = supportAcl,
        permissionsRepo = None
      )

      "is Internal when the permission was granted via the internal ACL" in {
        val (_, source) = checkUserPermissionWithSource(
          "user",
          fooDev.id,
          Instant.now(),
          janusData,
          Set.empty
        ).value
        source shouldEqual AccessSource.Internal
      }

      "is Admin when the permission was granted via admin access only" in {
        val (_, source) = checkUserPermissionWithSource(
          "admin",
          fooDev.id,
          Instant.now(),
          janusData,
          Set.empty
        ).value
        source shouldEqual AccessSource.Admin
      }

      "is Support when the permission was granted via support access only" in {
        val (_, source) = checkUserPermissionWithSource(
          "support.user",
          fooDev.id,
          Instant.now(),
          janusData,
          Set.empty
        ).value
        source shouldEqual AccessSource.Support
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
