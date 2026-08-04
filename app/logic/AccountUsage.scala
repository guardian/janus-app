package logic

import com.gu.janus.model.PermissionType.AccountPermission
import com.gu.janus.model.{
  ACL,
  AuditLog,
  AwsAccount,
  DeveloperPolicyGrant,
  Permission
}
import logic.DeveloperPolicies.developerPolicyDisplayName
import models.*

import java.time.Instant

/** Sets the access users hold in an AWS account against the access they have
  * actually requested, so unused permissions can be found.
  */
object AccountUsage {

  /** How often an access level was requested, and when it was last requested.
    */
  private case class Tally(lastUsed: Instant, count: Int)

  private case class UserTallies(
      permissions: Map[String, Tally],
      developerPolicies: Map[String, Tally]
  )

  private object UserTallies {
    val empty: UserTallies = UserTallies(Map.empty, Map.empty)
  }

  /** A developer policy access level from the audit trail: the policy name
    * decoded from its slug, the matching policy where the account still has
    * one, and how often it was requested.
    */
  private type PolicyTally = (String, Option[DeveloperPolicy], Tally)

  /** @param auditLogs
    *   Every audit trail entry for the account over the period, including any
    *   that could not be read.
    */
  def report(
      account: AwsAccount,
      acl: ACL,
      accountDeveloperPolicies: Set[DeveloperPolicy],
      auditLogs: Seq[Either[String, AuditLog]],
      policyCacheError: Option[String],
      from: Instant,
      to: Instant
  ): AccountUsageReport = {
    val (unreadable, entries) = auditLogs.partitionMap(identity)
    val defaultPermissions = acl.defaultPermissions.filter(_.account == account)
    val permissionsByUser = Accounts
      .accountPermissions(account, acl)
      .map(userPermissions =>
        userPermissions.userName ->
          (userPermissions.permissions ++ defaultPermissions)
      )
      .toMap
    val grantsByUser = acl.userAccess.view.mapValues(_.policyGrants).toMap
    val policiesByName =
      accountDeveloperPolicies.map(policy => policy.policyName -> policy).toMap

    val talliesByUser = tally(entries)

    def usageFor(userName: String): UserAccountUsage = {
      val tallies = talliesByUser.getOrElse(userName, UserTallies.empty)
      val permissions = permissionsByUser.getOrElse(userName, Set.empty)
      val grants = grantsByUser.getOrElse(userName, Set.empty)
      val grantedPolicyIds = grants.map(_.id)
      val grantedPolicies = accountDeveloperPolicies.filter(policy =>
        grantedPolicyIds.contains(policy.policyGrantId)
      )
      val policyTallies = tallies.developerPolicies.map { case (slug, tally) =>
        val policyName = developerPolicyDisplayName(slug)
        (policyName, policiesByName.get(policyName), tally)
      }
      UserAccountUsage(
        userName = userName,
        usedPermissions = usedPermissions(tallies.permissions, permissions),
        unusedPermissions = permissions
          .filterNot(permission =>
            tallies.permissions.contains(permission.label)
          )
          .toList
          .sortBy(_.label),
        grants = grantUsage(grants, grantedPolicies, policyTallies),
        unrecognisedUsage = unrecognisedUsage(policyTallies, grantedPolicies)
      )
    }

    val usersWithAccess = permissionsByUser.keySet
    val withAccess = usersWithAccess.toList.sorted.map(usageFor)
    val (withUsage, withoutUsage) =
      withAccess.partition(usage => lastActive(usage).isDefined)
    val withoutAccess =
      (talliesByUser.keySet -- usersWithAccess).toList.sorted.map(usageFor)

    AccountUsageReport(
      account = account,
      from = from,
      to = to,
      withoutUsage = withoutUsage,
      withUsage = withUsage,
      withoutAccess = withoutAccess,
      unreadableLogCount = unreadable.size,
      policyCacheError = policyCacheError
    )
  }

  /** Counts each user's requests by access level, keeping Janus permissions and
    * developer policies apart because they name their access levels
    * differently.
    *
    * Access granted through the superuser and support ACLs is excluded, since
    * the report covers the access part of the ACL only.
    */
  private def tally(auditLogs: Seq[AuditLog]): Map[String, UserTallies] = {
    def tallyLevels(entries: Seq[AuditLog]): Map[String, Tally] =
      entries
        .groupBy(_.accessLevel)
        .view
        .mapValues(group => Tally(group.map(_.instant).max, group.size))
        .toMap

    auditLogs
      .filterNot(_.external)
      .groupBy(_.username)
      .view
      .mapValues { entries =>
        val (permissions, developerPolicies) =
          entries.partition(_.permissionType == AccountPermission)
        UserTallies(tallyLevels(permissions), tallyLevels(developerPolicies))
      }
      .toMap
  }

  /** Access levels are matched to permissions where the user still holds them.
    * A used permission with no match has been revoked since it was used.
    */
  private def usedPermissions(
      tallies: Map[String, Tally],
      permissions: Set[Permission]
  ): List[UsedPermission] =
    tallies.toList
      .map { case (label, tally) =>
        UsedPermission(
          label = label,
          permission = permissions.find(_.label == label),
          lastUsed = tally.lastUsed,
          count = tally.count
        )
      }
      .sortBy(used => (-used.lastUsed.toEpochMilli, used.label))

  /** Splits the policies of each grant the user holds into used and unused.
    *
    * Only policies covered by one of the user's grants appear here; anything
    * else they used is reported as unrecognised.
    */
  private def grantUsage(
      grants: Set[DeveloperPolicyGrant],
      grantedPolicies: Set[DeveloperPolicy],
      policyTallies: Iterable[PolicyTally]
  ): List[GrantUsage] = {
    val talliesByPolicy = policyTallies.collect {
      case (_, Some(policy), tally) if grantedPolicies.contains(policy) =>
        policy -> tally
    }.toMap
    grants.toList
      .sortBy(_.name)
      .map { grant =>
        val policies = grantedPolicies.filter(_.policyGrantId == grant.id)
        GrantUsage(
          grant = grant,
          used = policies.toList
            .flatMap(policy =>
              talliesByPolicy
                .get(policy)
                .map(tally =>
                  UsedDeveloperPolicy(policy, tally.lastUsed, tally.count)
                )
            )
            .sortBy(used =>
              (-used.lastUsed.toEpochMilli, used.policy.policyName)
            ),
          unused = policies
            .filterNot(talliesByPolicy.contains)
            .toList
            .sortBy(_.policyName)
        )
      }
      .filter(usage => usage.used.nonEmpty || usage.unused.nonEmpty)
  }

  /** Developer policy usage that no grant the user holds covers, either because
    * the policy has been removed from the account, because the policy data
    * could not be loaded, or because the grant has been revoked since it was
    * used.
    */
  private def unrecognisedUsage(
      policyTallies: Iterable[PolicyTally],
      grantedPolicies: Set[DeveloperPolicy]
  ): List[UnrecognisedUsage] =
    policyTallies.toList
      .collect {
        case (policyName, policy, tally)
            if !policy.exists(grantedPolicies.contains) =>
          UnrecognisedUsage(policyName, tally.lastUsed, tally.count)
      }
      .sortBy(usage => (-usage.lastUsed.toEpochMilli, usage.name))

  /** Access the user holds and has used. Access used but no longer held is
    * excluded, so this can be read against [[grantedPermissionCount]].
    */
  def usedPermissionCount(usage: UserAccountUsage): Int =
    usage.usedPermissions.count(_.permission.isDefined) +
      usage.grants.map(_.used.size).sum

  def grantedPermissionCount(usage: UserAccountUsage): Int =
    usedPermissionCount(usage) + usage.unusedPermissions.size +
      usage.grants.map(_.unused.size).sum

  /** When the user last requested any access to the account during the period.
    */
  def lastActive(usage: UserAccountUsage): Option[Instant] =
    (usage.usedPermissions.map(_.lastUsed) ++
      usage.grants.flatMap(_.used.map(_.lastUsed)) ++
      usage.unrecognisedUsage.map(_.lastUsed)).maxOption

  /** Grants with something to show. A grant whose policies are all unused has
    * nothing to display where unused access is hidden.
    */
  def visibleGrants(
      usage: UserAccountUsage,
      showUnused: Boolean
  ): List[GrantUsage] =
    usage.grants.filter(grant => showUnused || grant.used.nonEmpty)

  def userCount(report: AccountUsageReport): Int =
    report.withoutUsage.size + report.withUsage.size

  /** Access granted to users of the account but not used during the period. */
  def unusedPermissionCount(report: AccountUsageReport): Int =
    (report.withoutUsage ++ report.withUsage).map { usage =>
      usage.unusedPermissions.size + usage.grants.map(_.unused.size).sum
    }.sum
}
