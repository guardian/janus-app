package logic

import com.gu.googleauth.UserIdentity
import com.gu.janus.model.*
import models.{AccountAccess, IamRoleInfo}

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
  ): Option[Map[AwsAccount, AccountAccess]] = {
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
          account -> AccountAccess(
            permsForAccount.toList,
            rolesForAccount.toList
          )
        }.toMap
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
  ): Option[Map[AwsAccount, AccountAccess]] = {
    if (isSupportUser(username, date, supportACL))
      Some(
        supportACL.supportAccess
          .groupBy(_.account)
          .map { case (account, permissions) =>
            account -> AccountAccess(permissions.toList, Nil)
          }
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
      userAccess(username, acl, provisionedRoles).getOrElse(Map.empty)
    val adminAccess =
      userAccess(username, adminACL, provisionedRoles).getOrElse(Map.empty)
    val supportAccess =
      userSupportAccess(username, date, supportACL).getOrElse(Map.empty)

    // combines these sources, merging permissions and roles from the three sources, for each account
    (access.keySet ++ adminAccess.keySet ++ supportAccess.keySet).map {
      account =>
        val permissions =
          access.get(account).map(_.permissions).getOrElse(Nil) ++
            adminAccess.get(account).map(_.permissions).getOrElse(Nil) ++
            supportAccess.get(account).map(_.permissions).getOrElse(Nil)
        val roles = access.get(account).map(_.iamRoles).getOrElse(Nil) ++
          adminAccess.get(account).map(_.iamRoles).getOrElse(Nil) ++
          supportAccess.get(account).map(_.iamRoles).getOrElse(Nil)
        account -> AccountAccess(permissions, roles)
    }.toMap
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
    val permission = allUserPermissions(
      username,
      date,
      acl,
      adminACL,
      supportACL,
      Nil
    ).flatMap { (awsAccount, accountAccess) =>
      val maybeMatchingPermission =
        accountAccess.permissions
          .find(_.id == permissionId)
      val maybeMatchingPermissionFromRole = accountAccess.iamRoles
        .map(_.asPermission)
        .find(_.id == permissionId)
      maybeMatchingPermission.orElse(maybeMatchingPermissionFromRole)
    }.headOption
    // TODO: calculate the access class instead of hard-coding this
    permission.map(_ -> AccessClass.Direct)
  }

  /** Check if the user has explicit access to this permission granted in the
    * Access.scala file.
    */
  def hasExplicitAccess(
      username: String,
      permission: Permission,
      acl: ACL,
      provisionedRoles: List[IamRoleInfo]
  ): Boolean = {
    userAccess(username, acl, Nil)
      .getOrElse(Map.empty)
      .exists { case (account, accountAccess) =>
        account == permission.account &&
        (accountAccess.permissions.contains(permission) ||
          accountAccess.iamRoles.exists(_.asPermission == permission))
      }
  }
}
