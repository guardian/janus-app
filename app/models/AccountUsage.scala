package models

import com.gu.janus.model.{AwsAccount, DeveloperPolicyGrant, Permission}

import java.time.Instant

/** A Janus permission a user requested during the reporting period.
  *
  * @param label
  *   The access level recorded in the audit log.
  * @param permission
  *   The matching permission, where the user still holds it.
  */
case class UsedPermission(
    label: String,
    permission: Option[Permission],
    lastUsed: Instant,
    count: Int
)

/** A developer policy a user requested during the reporting period. */
case class UsedDeveloperPolicy(
    policy: DeveloperPolicy,
    lastUsed: Instant,
    count: Int
)

/** Usage of the policies belonging to a single developer policy grant. */
case class GrantUsage(
    grant: DeveloperPolicyGrant,
    used: List[UsedDeveloperPolicy],
    unused: List[DeveloperPolicy]
)

/** Access that matches nothing the user currently holds: a developer policy
  * that has been removed from the account, or one that belongs to no grant the
  * user holds now.
  */
case class UnrecognisedUsage(name: String, lastUsed: Instant, count: Int)

/** One user's access to an account set against what they actually used.
  *
  * @param usedPermissions
  *   Most recently used first. A permission the user no longer holds is
  *   included, with nothing in [[UsedPermission.permission]].
  * @param unusedPermissions
  *   Alphabetical by label.
  */
case class UserAccountUsage(
    userName: String,
    usedPermissions: List[UsedPermission],
    unusedPermissions: List[Permission],
    grants: List[GrantUsage],
    unrecognisedUsage: List[UnrecognisedUsage]
)

/** Access and usage for every user of an account over a reporting period.
  *
  * Users are grouped as the report page presents them, each group sorted by
  * username.
  *
  * @param withoutUsage
  *   Users with access who requested nothing during the period.
  * @param withUsage
  *   Users with access who requested something during the period.
  * @param withoutAccess
  *   Users who requested access during the period but hold no permissions for
  *   the account now.
  * @param unreadableLogCount
  *   Audit log entries that could not be read, so counts may be understated.
  * @param policyCacheError
  *   Set when the account's developer policy data is unavailable, in which case
  *   policy usage is likely to appear as unrecognised.
  */
case class AccountUsageReport(
    account: AwsAccount,
    from: Instant,
    to: Instant,
    withoutUsage: List[UserAccountUsage],
    withUsage: List[UserAccountUsage],
    withoutAccess: List[UserAccountUsage],
    unreadableLogCount: Int,
    policyCacheError: Option[String]
)
