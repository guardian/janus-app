package e2e

import com.gu.janus.model.*
import com.gu.janus.policy.Iam.*

import java.time.Instant

/** This provides a means to build different [[JanusData]] fixtures. They can be
  * used in E2E tests by adding, for example:
  * `test.use({ janusData: "accounts-list" });` before a Playwright test.
  */
object E2EJanusData {
  private val denyAll = Policy(
    Seq(Statement(Effect.Deny, Seq(Action("*")), Seq(Resource("*"))))
  )

  private val emptyAccess = ACL(Map.empty)
  private val emptySupport = SupportACL.create(Map.empty, Set.empty)

  private val testAccount = AwsAccount("Test AWS Account", "test-aws-account")
  private val testAccount2 =
    AwsAccount("Mobile Apps AWS Account", "test-aws-account-2")
  private val testAccount3 =
    AwsAccount("Extremely Important AWS Account", "test-aws-account-3")

  private val testPermission = permission(testAccount)
  private val testPermission2 = permission(testAccount2)
  private val testPermission3 = permission(testAccount3)

  val fixtures: Map[String, JanusData] = Map(
    "index-permission" -> data(
      testAccount,
      access = grant(testPermission),
      support = emptySupport
    ),
    "accounts-list" -> data(
      testAccount2,
      access = grant(testPermission2),
      support = emptySupport
    ),
    "support-current" -> data(
      testAccount3,
      access = emptyAccess,
      support = currentSupport(testPermission3)
    )
  )

  private def permission(account: AwsAccount): Permission =
    Permission(
      account,
      "e2e-deny-all",
      "E2E test only - no AWS access",
      denyAll,
      shortTerm = true
    )

  private def grant(permission: Permission): ACL =
    ACL(Map("dev-user" -> ACLEntry(Set(permission), Set.empty)))

  private def currentSupport(permission: Permission): SupportACL =
    SupportACL.create(
      // The final rota slot stays active until another slot replaces it.
      Map(Instant.parse("2026-11-02T00:00:00Z") -> ("dev-user", "")),
      Set(permission)
    )

  private def data(
      account: AwsAccount,
      access: ACL,
      support: SupportACL
  ): JanusData =
    JanusData(
      Set(account),
      access,
      emptyAccess,
      support,
      permissionsRepo = None
    )
}
