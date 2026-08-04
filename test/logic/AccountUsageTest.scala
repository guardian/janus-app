package logic

import com.gu.janus.model.PermissionType
import com.gu.janus.model.PermissionType.{
  AccountPermission,
  DeveloperPolicyPermission
}
import com.gu.janus.model.{ACL, ACLEntry, AuditLog, JConsole}
import fixtures.Fixtures.*
import logic.AccountUsage.*
import logic.DeveloperPolicies.developerPolicySlug
import models.*
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.temporal.ChronoUnit.DAYS
import java.time.ZoneOffset.UTC
import java.time.{Duration, Instant, ZonedDateTime}

class AccountUsageTest extends AnyFreeSpec with Matchers with OptionValues {

  private val to = Instant.parse("2026-08-01T00:00:00Z")
  private val from = ZonedDateTime.ofInstant(to, UTC).minusMonths(3).toInstant

  private def daysBeforeEnd(days: Int): Instant = to.minus(days, DAYS)

  private val acl = ACL(
    Map(
      "test.user" -> ACLEntry(Set(fooDev, fooS3), Set.empty),
      "test.other" -> ACLEntry(Set(fooDev), Set.empty),
      "test.grantee" -> ACLEntry(Set(fooDev), Set(grantAlpha, grantBeta)),
      "test.different-account" -> ACLEntry(Set(barDev), Set.empty)
    ),
    Set.empty
  )

  private val fooPolicies =
    Set(
      developerPolicyAlphaFoo1,
      developerPolicyAlphaFoo2,
      developerPolicyBetaFoo
    )

  private def usage(
      username: String,
      accessLevel: String,
      instant: Instant,
      permissionType: PermissionType,
      external: Boolean = false
  ): Either[String, AuditLog] = Right(
    AuditLog(
      fooAct.authConfigKey,
      username,
      instant,
      Duration.ofHours(1),
      accessLevel,
      JConsole,
      external,
      permissionType
    )
  )

  private def permissionUsage(
      username: String,
      label: String,
      instant: Instant,
      external: Boolean = false
  ) = usage(username, label, instant, AccountPermission, external)

  private def policyUsage(
      username: String,
      policyName: String,
      instant: Instant,
      external: Boolean = false
  ) = usage(
    username,
    developerPolicySlug(policyName),
    instant,
    DeveloperPolicyPermission,
    external
  )

  private def report(
      auditLogs: Seq[Either[String, AuditLog]],
      developerPolicies: Set[DeveloperPolicy] = fooPolicies,
      policyCacheError: Option[String] = None
  ): AccountUsageReport = AccountUsage.report(
    account = fooAct,
    acl = acl,
    accountDeveloperPolicies = developerPolicies,
    auditLogs = auditLogs,
    policyCacheError = policyCacheError,
    from = from,
    to = to
  )

  private def userNamed(
      users: List[UserAccountUsage],
      username: String
  ): UserAccountUsage = users.find(_.userName == username).value

  "report" - {
    "only covers users with permissions in the account" in {
      val result = report(Nil)
      (result.withoutUsage ++ result.withUsage).map(
        _.userName
      ) should contain theSameElementsAs List(
        "test.user",
        "test.other",
        "test.grantee"
      )
    }

    "sorts each group by username" in {
      val result = report(Nil)
      result.withoutUsage.map(_.userName) shouldEqual result.withoutUsage
        .map(_.userName)
        .sorted
    }

    "puts a user who requested nothing in the unused group" in {
      val result = report(Nil)
      result.withUsage shouldBe empty
      result.withoutUsage.map(_.userName) should contain("test.user")
    }

    "puts a user who requested something in the used group" in {
      val result = report(
        Seq(permissionUsage("test.user", fooDev.label, daysBeforeEnd(1)))
      )
      result.withUsage.map(_.userName) shouldEqual List("test.user")
      result.withoutUsage.map(_.userName) should not contain "test.user"
    }

    "reports the whole account as unused when there are no logs" in {
      unusedPermissionCount(report(Nil)) shouldEqual 7
    }
  }

  "used and unused permissions" - {
    "splits a user's permissions by whether they were requested" in {
      val result = report(
        Seq(permissionUsage("test.user", fooDev.label, daysBeforeEnd(1)))
      )
      val usage = userNamed(result.withUsage, "test.user")
      usage.usedPermissions.map(_.label) shouldEqual List(fooDev.label)
      usage.unusedPermissions shouldEqual List(fooS3)
    }

    "counts requests and records the most recent" in {
      val result = report(
        Seq(
          permissionUsage("test.user", fooDev.label, daysBeforeEnd(10)),
          permissionUsage("test.user", fooDev.label, daysBeforeEnd(2)),
          permissionUsage("test.user", fooDev.label, daysBeforeEnd(30))
        )
      )
      val used = userNamed(result.withUsage, "test.user").usedPermissions.head
      used.count shouldEqual 3
      used.lastUsed shouldEqual daysBeforeEnd(2)
    }

    "resolves a used permission the user still holds" in {
      val result = report(
        Seq(permissionUsage("test.user", fooDev.label, daysBeforeEnd(1)))
      )
      userNamed(
        result.withUsage,
        "test.user"
      ).usedPermissions.head.permission.value shouldEqual fooDev
    }

    "leaves a used permission unresolved when the user does not hold it" in {
      val result = report(
        Seq(permissionUsage("test.other", fooS3.label, daysBeforeEnd(1)))
      )
      val used = userNamed(result.withUsage, "test.other").usedPermissions.head
      used.label shouldEqual fooS3.label
      used.permission shouldBe empty
    }

    "lists used permissions most recent first" in {
      val result = report(
        Seq(
          permissionUsage("test.user", fooDev.label, daysBeforeEnd(20)),
          permissionUsage("test.user", fooS3.label, daysBeforeEnd(3))
        )
      )
      userNamed(result.withUsage, "test.user").usedPermissions.map(
        _.label
      ) shouldEqual List(fooS3.label, fooDev.label)
    }

    "includes default permissions for the account in a user's access" in {
      val aclWithDefault = acl.copy(defaultPermissions = Set(fooCf, barCf))
      val result = AccountUsage.report(
        fooAct,
        aclWithDefault,
        Set.empty,
        Nil,
        None,
        from,
        to
      )
      userNamed(
        result.withoutUsage,
        "test.other"
      ).unusedPermissions should contain theSameElementsAs List(fooDev, fooCf)
    }
  }

  "developer policies" - {
    "groups a user's policies by grant" in {
      val result = report(Nil)
      userNamed(result.withoutUsage, "test.grantee").grants.map(
        _.grant
      ) shouldEqual List(grantAlpha, grantBeta)
    }

    "omits grants that have no policies in this account" in {
      val result = report(Nil, developerPolicies = Set(developerPolicyBetaFoo))
      userNamed(result.withoutUsage, "test.grantee").grants.map(
        _.grant
      ) shouldEqual List(grantBeta)
    }

    "splits a grant's policies by whether they were requested" in {
      val result = report(
        Seq(
          policyUsage(
            "test.grantee",
            developerPolicyAlphaFoo1.policyName,
            daysBeforeEnd(4)
          )
        )
      )
      val alpha =
        userNamed(result.withUsage, "test.grantee").grants
          .find(_.grant == grantAlpha)
          .value
      alpha.used.map(_.policy) shouldEqual List(developerPolicyAlphaFoo1)
      alpha.unused shouldEqual List(developerPolicyAlphaFoo2)
    }

    "counts policy requests and records the most recent" in {
      val result = report(
        Seq(
          policyUsage(
            "test.grantee",
            developerPolicyBetaFoo.policyName,
            daysBeforeEnd(40)
          ),
          policyUsage(
            "test.grantee",
            developerPolicyBetaFoo.policyName,
            daysBeforeEnd(5)
          )
        )
      )
      val used = userNamed(result.withUsage, "test.grantee").grants
        .find(_.grant == grantBeta)
        .value
        .used
        .head
      used.count shouldEqual 2
      used.lastUsed shouldEqual daysBeforeEnd(5)
    }

    "counts policy usage towards a user being active" in {
      val result = report(
        Seq(
          policyUsage(
            "test.grantee",
            developerPolicyAlphaFoo1.policyName,
            daysBeforeEnd(4)
          )
        )
      )
      result.withUsage.map(_.userName) shouldEqual List("test.grantee")
    }

    "reports usage of a policy that is no longer in the account as not currently granted" in {
      val result = report(
        Seq(policyUsage("test.grantee", "removed-policy", daysBeforeEnd(6)))
      )
      val usage = userNamed(result.withUsage, "test.grantee")
      usage.unrecognisedUsage.map(_.name) shouldEqual List("removed-policy")
      usage.grants.flatMap(_.used) shouldBe empty
    }

    "reports usage of a policy the user's grants no longer cover" in {
      val result = report(
        Seq(
          policyUsage(
            "test.user",
            developerPolicyAlphaFoo1.policyName,
            daysBeforeEnd(6)
          )
        )
      )
      val usage = userNamed(result.withUsage, "test.user")
      usage.unrecognisedUsage.map(_.name) shouldEqual List(
        developerPolicyAlphaFoo1.policyName
      )
    }

    "counts usage of a policy the user's grants no longer cover as activity" in {
      val result = report(
        Seq(
          policyUsage(
            "test.user",
            developerPolicyAlphaFoo1.policyName,
            daysBeforeEnd(6)
          )
        )
      )
      result.withUsage.map(_.userName) should contain("test.user")
      result.withoutUsage.map(_.userName) should not contain "test.user"
    }

    "does not report a granted policy that was used as unrecognised" in {
      val result = report(
        Seq(
          policyUsage(
            "test.grantee",
            developerPolicyAlphaFoo1.policyName,
            daysBeforeEnd(6)
          )
        )
      )
      userNamed(
        result.withUsage,
        "test.grantee"
      ).unrecognisedUsage shouldBe empty
    }

    "decodes slugs when matching, so policy names with special characters resolve" in {
      val awkward = developerPolicyAlphaFoo1.copy(policyName = "alpha policy.1")
      val result = report(
        Seq(policyUsage("test.grantee", awkward.policyName, daysBeforeEnd(6))),
        developerPolicies = Set(awkward)
      )
      val usage = userNamed(result.withUsage, "test.grantee")
      usage.unrecognisedUsage shouldBe empty
      usage.grants.flatMap(_.used).map(_.policy) shouldEqual List(awkward)
    }
  }

  "users found only in the audit log" - {
    "are reported separately" in {
      val result = report(
        Seq(permissionUsage("test.stranger", fooDev.label, daysBeforeEnd(7)))
      )
      result.withoutAccess.map(_.userName) shouldEqual List("test.stranger")
      (result.withUsage ++ result.withoutUsage).map(
        _.userName
      ) should not contain "test.stranger"
    }

    "have their usage recorded but hold nothing unused" in {
      val result = report(
        Seq(permissionUsage("test.stranger", fooDev.label, daysBeforeEnd(7)))
      )
      val usage = result.withoutAccess.head
      usage.usedPermissions.map(_.label) shouldEqual List(fooDev.label)
      usage.unusedPermissions shouldBe empty
      usage.grants shouldBe empty
    }

    "have their developer policy usage recorded" in {
      val result = report(
        Seq(
          policyUsage(
            "test.stranger",
            developerPolicyAlphaFoo1.policyName,
            daysBeforeEnd(7)
          )
        )
      )
      val usage = result.withoutAccess.head
      usage.unrecognisedUsage.map(_.name) shouldEqual List(
        developerPolicyAlphaFoo1.policyName
      )
      lastActive(usage).value shouldEqual daysBeforeEnd(7)
    }

    "are not counted as users with access" in {
      val result = report(
        Seq(permissionUsage("test.stranger", fooDev.label, daysBeforeEnd(7)))
      )
      userCount(result) shouldEqual 3
    }
  }

  "summary figures" - {
    "count a user's granted permissions across permissions and policies" in {
      val usage = userNamed(report(Nil).withoutUsage, "test.grantee")
      grantedPermissionCount(usage) shouldEqual 4
      usedPermissionCount(usage) shouldEqual 0
    }

    "count used permissions across permissions and policies" in {
      val result = report(
        Seq(
          permissionUsage("test.grantee", fooDev.label, daysBeforeEnd(2)),
          policyUsage(
            "test.grantee",
            developerPolicyAlphaFoo1.policyName,
            daysBeforeEnd(3)
          )
        )
      )
      usedPermissionCount(
        userNamed(result.withUsage, "test.grantee")
      ) shouldEqual 2
    }

    "leave permissions the user does not hold out of the counts" in {
      val result = report(
        Seq(permissionUsage("test.other", fooS3.label, daysBeforeEnd(1)))
      )
      val usage = userNamed(result.withUsage, "test.other")
      usage.usedPermissions should have size 1
      usedPermissionCount(usage) shouldEqual 0
      grantedPermissionCount(usage) shouldEqual 1
    }

    "report the most recent activity as the last active time" in {
      val result = report(
        Seq(
          permissionUsage("test.user", fooDev.label, daysBeforeEnd(9)),
          permissionUsage("test.user", fooS3.label, daysBeforeEnd(2))
        )
      )
      lastActive(
        userNamed(result.withUsage, "test.user")
      ).value shouldEqual daysBeforeEnd(2)
    }

    "have no last active time when nothing was used" in {
      lastActive(
        userNamed(report(Nil).withoutUsage, "test.user")
      ) shouldBe empty
    }
  }

  "external access" - {
    "is left out of permission usage" in {
      val result = report(
        Seq(
          permissionUsage(
            "test.user",
            fooDev.label,
            daysBeforeEnd(1),
            external = true
          )
        )
      )
      userNamed(result.withoutUsage, "test.user").usedPermissions shouldBe empty
      result.withUsage shouldBe empty
    }

    "is left out of developer policy usage" in {
      val result = report(
        Seq(
          policyUsage(
            "test.grantee",
            developerPolicyAlphaFoo1.policyName,
            daysBeforeEnd(1),
            external = true
          )
        )
      )
      val usage = userNamed(result.withoutUsage, "test.grantee")
      usage.grants.flatMap(_.used) shouldBe empty
      usage.unrecognisedUsage shouldBe empty
    }

    "does not put a user without access into the report" in {
      val result = report(
        Seq(
          permissionUsage(
            "test.stranger",
            fooDev.label,
            daysBeforeEnd(1),
            external = true
          )
        )
      )
      result.withoutAccess shouldBe empty
    }

    "does not stop the same user's own access being counted" in {
      val result = report(
        Seq(
          permissionUsage(
            "test.user",
            fooDev.label,
            daysBeforeEnd(5),
            external = true
          ),
          permissionUsage("test.user", fooDev.label, daysBeforeEnd(3))
        )
      )
      val usage = userNamed(result.withUsage, "test.user")
      usage.usedPermissions.map(used => (used.label, used.count)) shouldEqual
        List((fooDev.label, 1))
      lastActive(usage).value shouldEqual daysBeforeEnd(3)
    }
  }

  "visibleGrants" - {
    "keeps a grant with usage when unused access is hidden" in {
      val result = report(
        Seq(
          policyUsage(
            "test.grantee",
            developerPolicyAlphaFoo1.policyName,
            daysBeforeEnd(1)
          )
        )
      )
      val usage = userNamed(result.withUsage, "test.grantee")
      visibleGrants(usage, showUnused = false).map(
        _.grant.name
      ) shouldEqual List(grantAlpha.name)
    }

    "drops a grant with nothing used when unused access is hidden" in {
      val usage = userNamed(report(Nil).withoutUsage, "test.grantee")
      usage.grants should not be empty
      visibleGrants(usage, showUnused = false) shouldBe empty
    }

    "keeps every grant when unused access is shown" in {
      val usage = userNamed(report(Nil).withoutUsage, "test.grantee")
      visibleGrants(usage, showUnused = true) shouldEqual usage.grants
    }
  }

  "passes through the reporting period and warnings" in {
    val result =
      report(Seq.fill(4)(Left("bad row")), policyCacheError = Some("boom"))
    result.account shouldEqual fooAct
    result.from shouldEqual from
    result.to shouldEqual to
    result.unreadableLogCount shouldEqual 4
    result.policyCacheError.value shouldEqual "boom"
  }
}
