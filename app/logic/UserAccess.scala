package logic

import com.gu.googleauth.UserIdentity
import com.gu.janus.model.*
import logic.DeveloperPolicies.{policyGrantsForUser, toPermission}
import models.*
import models.AccessSource.{Admin, Internal, Support}

import java.time.Instant

object UserAccess {

  /** Extract the username from the pan-domain user.
    */
  def username(user: UserIdentity): String =
    user.email.split("@").head.toLowerCase

  /** A user's access in the internal ACL. Note that default permissions are
    * available for anyone mentioned in the ACL.
    *
    * If the user appears in the ACL, this will return an [[AccountAccess]] per
    * [[AwsAccount]], preserving the distinction between static Janus
    * permissions and dynamically-loaded developer policies. There will be a key
    * for any AWS account to which the user has access.
    */
  def internalUserAccess(
      username: String,
      janusData: JanusData,
      developerPolicies: Set[DeveloperPolicy]
  ): Option[Map[AwsAccount, AccountAccess]] = {
    val access = totalUserAccess(
      username,
      internalAcl = Some(janusData.access),
      adminAcl = None,
      supportData = None,
      developerPolicies
    )
    Option.when(access.nonEmpty)(
      access.view
        .mapValues(sourced =>
          AccountAccess(
            permissions = sourced.internal.permissions,
            developerPolicies = sourced.internal.developerPolicies
          )
        )
        .toMap
    )
  }

  /** A user's access in the admin ACL.
    *
    * As above, if the user appears in the ACL, this will return an
    * [[AccountAccess]] for all AWS accounts to which the user has access.
    */
  def adminUserAccess(
      username: String,
      janusData: JanusData,
      developerPolicies: Set[DeveloperPolicy]
  ): Option[Map[AwsAccount, AccountAccess]] = {
    val access = totalUserAccess(
      username,
      internalAcl = None,
      adminAcl = Some(janusData.admin),
      supportData = None,
      developerPolicies
    )
    Option.when(access.nonEmpty)(
      access.view
        .mapValues(sourced =>
          AccountAccess(
            permissions = sourced.admin.permissions,
            developerPolicies = sourced.admin.developerPolicies
          )
        )
        .toMap
    )
  }

  /** Checks if the username is explicitly mentioned in the provided ACL.
    */
  def hasAccess(username: String, acl: ACL): Boolean = {
    acl.userAccess.contains(username)
  }

  /** Check if the provided user has been granted the permission with the given
    * ID and, if so, which ACL gave access.
    *
    * The source is useful because it allows us to include it in the audit log.
    */
  def checkUserPermissionWithSource(
      username: String,
      permissionId: String,
      date: Instant,
      janusData: JanusData,
      developerPolicies: Set[DeveloperPolicy]
  ): Option[(Permission, AccessSource)] = {
    def bySource(
        access: SourcedAccountAccess
    ): List[(AccountAccess, AccessSource)] = List(
      access.internal -> Internal,
      access.admin -> Admin,
      access.support -> Support
    )

    totalUserAccess(
      username,
      Some(janusData.access),
      Some(janusData.admin),
      Some((janusData.support, date)),
      developerPolicies
    ).valuesIterator.flatMap { access =>
      bySource(access).flatMap((aa, src) =>
        val policyGrants = src match {
          case Internal => policyGrantsForUser(username, acl = janusData.access)
          case Admin    => policyGrantsForUser(username, acl = janusData.admin)
          case Support  => Set.empty
        }
        (aa.permissions ++ aa.developerPolicies.flatMap { policy =>
          policyGrants
            .find(_.id == policy.policyGrantId)
            .map(toPermission(policy, _))
        })
          .find(_.id == permissionId)
          .map((_, src))
      )
    }.nextOption
  }

  /** This is the central logic for the public-facing methods that need to
    * determine what access a user has.
    *
    * The given ACLs are merged to give a single model of the user's total
    * access. The result is that for all accounts the user has access to we have
    * a rich model of the source of each permission and the type of permission
    * granted to the user.
    *
    * The individual ACLs are all optional so that the total access given
    * depends on the context of the call. In some cases we don't need to know
    * about support access and in others we don't need to know about admin
    * access.
    *
    * @param username
    *   User whose access we're determining
    * @param internalAcl
    *   If included, results will include access from this ACL
    * @param adminAcl
    *   If included, results will include access from this admin ACL
    * @param supportData
    *   If included, results will include support access. The second value of
    *   this pair is the instant in the support rota where access is being
    *   determined. So it will only include the user if they have a shift on the
    *   support rota that's active at the given instant.
    * @param developerPolicies
    *   All the [[DeveloperPolicy]]s that Janus knows about. The user might have
    *   a grant for some of these and their permissions will be included in the
    *   method result.
    */
  private def totalUserAccess(
      username: String,
      internalAcl: Option[ACL],
      adminAcl: Option[ACL],
      supportData: Option[(SupportACL, Instant)],
      developerPolicies: Set[DeveloperPolicy]
  ): Map[AwsAccount, SourcedAccountAccess] = {
    def userAccess(
        username: String,
        acl: ACL,
        developerPolicies: Set[DeveloperPolicy]
    ): Option[Map[AwsAccount, AccountAccess]] =
      acl.userAccess
        .get(username)
        .map { aclEntry =>
          val permissions = aclEntry.permissions ++ acl.defaultPermissions
          val grantedPolicyIds = aclEntry.policyGrants.map(_.id)
          val matchedPolicies = developerPolicies.filter { policy =>
            grantedPolicyIds.contains(policy.policyGrantId)
          }

          val permsByAccount =
            permissions.groupBy(_.account).view.mapValues(_.toList).toMap

          val policiesByAccount =
            matchedPolicies.groupBy(_.account).view.mapValues(_.toList).toMap

          val allAccounts = permsByAccount.keySet ++ policiesByAccount.keySet
          allAccounts.map { account =>
            account -> AccountAccess(
              permissions = permsByAccount.getOrElse(account, Nil),
              developerPolicies = policiesByAccount.getOrElse(account, Nil)
            )
          }.toMap
        }

    val internal =
      internalAcl
        .flatMap(acl => userAccess(username, acl, developerPolicies))
        .getOrElse(Map.empty)

    val admin =
      adminAcl
        .flatMap(acl => userAccess(username, acl, developerPolicies))
        .getOrElse(Map.empty)

    val support =
      supportData
        .map((acl, when) =>
          val perms =
            userSupportAccess(username, when, acl).getOrElse(Set.empty)
          perms
            .groupBy(_.account)
            .view
            .mapValues(ps =>
              AccountAccess(permissions = ps.toList, developerPolicies = Nil)
            )
            .toMap
        )
        .getOrElse(Map.empty)

    val allAccounts = internal.keySet ++ admin.keySet ++ support.keySet

    allAccounts.map { account =>
      account -> SourcedAccountAccess(
        internal = internal.getOrElse(account, AccountAccess.empty),
        admin = admin.getOrElse(account, AccountAccess.empty),
        support = support.getOrElse(account, AccountAccess.empty)
      )
    }.toMap
  }

  def userSupportAccess(
      username: String,
      date: Instant,
      supportACL: SupportACL
  ): Option[Set[Permission]] = {
    if (isSupportUser(username, date, supportACL))
      Some(supportACL.supportAccess)
    else None
  }

  def isSupportUser(
      username: String,
      date: Instant,
      supportACL: SupportACL
  ): Boolean = {
    activeSupportUsers(date, supportACL).exists {
      case (_, (maybeUser1, maybeUser2)) =>
        maybeUser1.contains(username) || maybeUser2.contains(username)
    }
  }

  /** Returns the usernames of the support personnel at the given instant,
    * converting empty (TBD) users to None.
    */
  def activeSupportUsers(
      date: Instant,
      supportACL: SupportACL
  ): Option[(Instant, (Option[String], Option[String]))] = {
    supportACL.rota.toList
      .sortBy((startTime, _) => startTime)
      // Last slot start-time before given instant
      .findLast((startTime, _) => startTime.isBefore(date))
      .map { case (startTime, (user1, user2)) =>
        startTime -> (
          if (user1.isEmpty) None else Some(user1),
          if (user2.isEmpty) None else Some(user2)
        )
      }
  }

  def nextSupportUsers(
      date: Instant,
      supportACL: SupportACL
  ): Option[(Instant, (Option[String], Option[String]))] = {
    supportACL.rota.toList
      .sortBy((startTime, _) => startTime)
      // First slot start-time after or at given instant
      .find((startTime, _) => startTime.isAfter(date))
      .map { case (startTime, (user1, user2)) =>
        startTime -> (
          if (user1.isEmpty) None else Some(user1),
          if (user2.isEmpty) None else Some(user2)
        )
      }
  }

  /** Returns the start and end times of future (after active and next) slots
    * for a given user
    * @return
    *   Tuple of the start date and the other user on the rota
    */
  def futureRotaSlotsForUser(
      date: Instant,
      supportACL: SupportACL,
      user: String
  ): List[(Instant, String)] = {
    val nextSlotStartTime =
      nextSupportUsers(date, supportACL).map((startTime, _) => startTime)
    nextSlotStartTime
      .map { nextSlot =>
        supportACL.rota.toList
          .sortBy((startTime, _) => startTime)
          // Collecting all slots after the 'next' slot for the given user
          .collect {
            case (startTime, (user1, user2))
                if startTime.isAfter(
                  nextSlot
                ) && (user1 == user || user2 == user) =>
              (startTime, if (user1 == user) user2 else user1)
          }
      }
      .getOrElse(Nil)
  }
}
