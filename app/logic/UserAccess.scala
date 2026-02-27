package logic

import com.gu.googleauth.UserIdentity
import com.gu.janus.model.{ACL, AwsAccount, Permission, SupportACL}
import logic.DeveloperPolicies.toPermission
import models.DeveloperPolicy

import java.time.Instant

object UserAccess {

  /** Extract the username from the pan-domain user.
    */
  def username(user: UserIdentity): String =
    user.email.split("@").head.toLowerCase

  /** A user's basic access. Note that default permissions are available for
    * anyone mentioned in the Access list.
    */
  def userAccess(
      username: String,
      acl: ACL,
      developerPolicies: Set[DeveloperPolicy]
  ): Option[Set[Permission]] =
    acl.userAccess
      .get(username)
      .map { aclEntry =>
        val permissions = aclEntry.permissions ++ acl.defaultPermissions
        val grantedPolicyIds = aclEntry.policyGrants.map(_.id)
        val policies = developerPolicies
          .filter { policy =>
            grantedPolicyIds.contains(policy.policyGrantId)
          }
        permissions ++ policies.map(toPermission)
      }

  /** Checks if the username is explicitly mentioned in the provided ACL.
    */
  def hasAccess(username: String, acl: ACL): Boolean = {
    acl.userAccess.keySet.contains(username)
  }

  // support access

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

  /** Combine a user's permissions from all sources to work out everything they
    * can do.
    */
  private def allUserPermissions(
      username: String,
      date: Instant,
      acl: ACL,
      adminACL: ACL,
      supportACL: SupportACL,
      developerPolicies: Set[DeveloperPolicy]
  ): Set[Permission] = {
    val access =
      userAccess(username, acl, developerPolicies).getOrElse(Set.empty)
    val adminAccess =
      userAccess(username, adminACL, developerPolicies).getOrElse(Set.empty)
    val supportAccess =
      userSupportAccess(username, date, supportACL).getOrElse(Set.empty)
    access ++ adminAccess ++ supportAccess
  }

  /** All the accounts this user has any access to.
    */
  def allUserAccounts(
      username: String,
      date: Instant,
      acl: ACL,
      adminACL: ACL,
      supportACL: SupportACL,
      developerPolicies: Set[DeveloperPolicy]
  ): Set[AwsAccount] = {
    allUserPermissions(
      username,
      date,
      acl,
      adminACL,
      supportACL,
      developerPolicies
    ).map(_.account)
  }

  /** Check if the provided user has been granted this permission, and whether
    * that permission was granted via explicit (ie. non-support, non-admin)
    * access.
    *
    * @return
    *   Some(permission, isExplicitAccess) iff user has the permission.
    */
  def checkUserPermissionWithSource(
      username: String,
      permissionId: String,
      date: Instant,
      acl: ACL,
      adminACL: ACL,
      supportACL: SupportACL,
      developerPolicies: Set[DeveloperPolicy]
  ): Option[(Permission, Boolean)] = {
    val explicitAccess = userAccess(username, acl, developerPolicies).getOrElse(Set.empty)
    val adminAccess = userAccess(username, adminACL, developerPolicies).getOrElse(Set.empty)
    val supportAccess = userSupportAccess(username, date, supportACL).getOrElse(Set.empty)
    val allPerms = explicitAccess ++ adminAccess ++ supportAccess
    allPerms
      .find(_.id == permissionId)
      .map(permission => (permission, explicitAccess.contains(permission)))
  }

  /** Checks if a user has access to an account and returns appropriate
    * permissions (if any).
    */
  def userAccountAccess(
      username: String,
      accountId: String,
      date: Instant,
      acl: ACL,
      adminACL: ACL,
      supportACL: SupportACL,
      developerPolicies: Set[DeveloperPolicy]
  ): Set[Permission] = {
    allUserPermissions(
      username,
      date,
      acl,
      adminACL,
      supportACL,
      developerPolicies
    )
      .filter(_.account.authConfigKey == accountId)
  }
}
