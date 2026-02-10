package logic

import com.gu.googleauth.UserIdentity
import com.gu.janus.model.{ACL, ACLEntry, AccessClass, AwsAccount, Permission, SupportACL}
import models.{AccountAccess, AwsAccountAccess, IamRoleInfo}

import java.time.Instant

object UserAccess {

  /** Extract the username from the pan-domain user.
    */
  def username(user: UserIdentity): String =
    user.email.split("@").head.toLowerCase

  /** A user's standard (excluding admin/support) access for each AwsAccount.
    *
    * The ACL's default permissions are available for anyone mentioned in the
    * Access list.
    */
  def userAccess(
      username: String,
      acl: ACL,
      provisionedRoles: List[IamRoleInfo]
  ): Option[Set[AwsAccountAccess]] = {
    acl.userAccess
      .get(username)
      .map { aclEntry =>
        val permissions =
          userPermissions(username, aclEntry, acl.defaultPermissions)
        val roles = userRoles(username, provisionedRoles, aclEntry)
        val allMentionedAccounts =
          permissions.map(_.account) ++ roles.map(_.account)

        allMentionedAccounts.map { account =>
          val permsForAccount = permissions.filter(_.account == account)
          val rolesForAccount = roles.filter(_.account == account)
          AwsAccountAccess(
            account,
            permsForAccount.toList,
            rolesForAccount.toList
          )
        }
      }
  }

  private def userPermissions(
      username: String,
      aclEntry: ACLEntry,
      defaultPermissions: Set[Permission]
  ): Set[Permission] = {
    aclEntry.permissions ++ defaultPermissions
  }

  private def userRoles(
      username: String,
      provisionedRoles: List[IamRoleInfo],
      aclEntry: ACLEntry
  ): Set[IamRoleInfo] = {
    val eligibleRoleTags = aclEntry.roles
    provisionedRoles.filter { role =>
      eligibleRoleTags
        .exists(tag => tag.iamRoleTag == role.provisionedRoleTagValue)
    }.toSet
  }

  /** Checks if the username is explicitly mentioned in the provided ACL.
    */
  def hasAnyAccess(username: String, acl: ACL): Boolean = {
    acl.userAccess.keySet.contains(username)
  }

  // support access

  def userSupportAccess(
      username: String,
      date: Instant,
      supportACL: SupportACL
  ): Option[Set[AwsAccountAccess]] = {
    if (isSupportUser(username, date, supportACL))
      Some(
        supportACL.supportAccess
          .groupBy(_.account)
          .map { case (account, permissions) =>
            AwsAccountAccess(account, permissions.toList, Nil)
          }
          .toSet
      )
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
    *
    * TODO: extend this to include provisioned roles support
    */
  def allUserPermissions(
      username: String,
      date: Instant,
      acl: ACL,
      adminACL: ACL,
      supportACL: SupportACL,
      provisionedRoles: List[IamRoleInfo]
  ): Map[AwsAccount, AccountAccess] = {
    val access =
      userAccess(username, acl, provisionedRoles).getOrElse(Set.empty)
    val adminAccess =
      userAccess(username, adminACL, provisionedRoles).getOrElse(Set.empty)
    val supportAccess =
      userSupportAccess(username, date, supportACL).getOrElse(Set.empty)

    (access ++ adminAccess ++ supportAccess).groupBy(_.awsAccount)
      .map { case (account, accesses) =>
        AwsAccountAccess(
          account,
          accesses.flatMap(_.permissions).toList,
          accesses.flatMap(_.iamRoles).toList
        )
      }.toSet
  }

  /** Check if the provider user has been granted this permission.
   * 
   * TODO: have this check for externality at the same time, and return it
    */
  def checkUserPermission(
      username: String,
      permissionId: String,
      date: Instant,
      acl: ACL,
      adminACL: ACL,
      supportACL: SupportACL,
      iamRoles: List[IamRoleInfo]
  ): Option[(Permission, AccessClass)] = {
    allUserPermissions(username, date, acl, adminACL, supportACL, Nil)
      .flatMap { awsAccountAccess =>
        val maybeMatchingPermission =
          awsAccountAccess.permissions
            .find(_.id == permissionId)
        val maybeMatchingPermissionFromRole = awsAccountAccess.iamRoles
          .map(_.asPermission)
          .find(_.id == permissionId)
        maybeMatchingPermission.orElse(maybeMatchingPermissionFromRole)
      }
      .headOption
  }

  /** Check if the user has explicit access to this permission granted in the
    * Access.scala file.
    *
    * TODO: extend this to include provisioned roles support
    */
  def hasExplicitAccess(
      username: String,
      permission: Permission,
      acl: ACL,
      provisionedRoles: List[IamRoleInfo]
  ): Boolean = {
    userAccess(username, acl, Nil)
      .getOrElse(Set.empty)
      .exists(_.permissions.contains(permission))
  }
}
