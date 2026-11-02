import com.gu.janus.model.*
import com.gu.janus.policy.Iam.*

import java.time.Instant

/** Named [[JanusData]] fixtures for Playwright tests. Select one with
  * `test.use({ janusData: "accounts-list" })`.
  */
private object E2EJanusData {
  private val denyAll = Policy(
    Seq(Statement(Effect.Deny, Seq(Action("*")), Seq(Resource("*"))))
  )
  private val emptyAccess = ACL(Map.empty)
  private val emptySupport = SupportACL.create(Map.empty, Set.empty)
  private val indexAccount = AwsAccount("Test AWS Account", "test-aws-account")
  private val accountsAccount =
    AwsAccount("Mobile Apps AWS Account", "mobile-apps-aws-account")
  private val supportAccount =
    AwsAccount("Very Important AWS Account", "very-important-aws-account")
  private val indexPermission = permission(indexAccount)
  private val accountsPermission = permission(accountsAccount)
  private val supportPermission = permission(supportAccount)

  val fixtures: Map[String, JanusData] = Map(
    "index-permission" -> data(
      indexAccount,
      access = grant(indexPermission),
      support = emptySupport
    ),
    "accounts-list" -> data(
      accountsAccount,
      access = grant(accountsPermission),
      support = emptySupport
    ),
    "support-current" -> data(
      supportAccount,
      access = emptyAccess,
      support = currentSupport(supportPermission)
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
