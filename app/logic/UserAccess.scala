package logic

import com.gu.googleauth.UserIdentity
import com.gu.janus.model.{ACL, AwsAccount, Permission, SupportACL}
import org.joda.time.DateTime

object UserAccess {

  /** Extract the username from the pan-domain user.
    */
  def username(user: UserIdentity): String =
    user.email.split("@").head.toLowerCase

  /** A user's basic access. Note that default permissions are available for
    * anyone mentioned in the Access list.
    */
  def userAccess(username: String, acl: ACL): Option[Set[Permission]] = {
    acl.userAccess
      .get(username)
      .map(permissions => permissions ++ acl.defaultPermissions)
  }

  /** Checks if the username is explicitly mentioned in the provided ACL.
    */
  def hasAccess(username: String, acl: ACL): Boolean = {
    acl.userAccess.keySet.contains(username)
  }

  // support access

  def userSupportAccess(
      username: String,
      date: DateTime,
      supportACL: SupportACL
  ): Option[Set[Permission]] = {
    if (isSupportUser(username, date, supportACL))
      Some(supportACL.supportAccess)
    else None
  }

  def isSupportUser(
      username: String,
      date: DateTime,
      supportACL: SupportACL
  ): Boolean = {
    activeSupportUsers(date, supportACL).exists {
      case (_, (maybeUser1, maybeUser2)) =>
        maybeUser1.contains(username) || maybeUser2.contains(username)
    }
  }

  /** Returns the usernames of the current support personnel, converting empty
    * (TBD) users to None.
    */
  def activeSupportUsers(
      date: DateTime,
      supportACL: SupportACL
  ): Option[(DateTime, (Option[String], Option[String]))] = {
    supportACL.rota.find { case (startTime, _) =>
      date.isAfter(startTime) && date.isBefore(
        startTime.plus(supportACL.supportPeriod)
      )
    } map { case (startTime, (user1, user2)) =>
      startTime -> (
        if (user1.isEmpty) None else Some(user1),
        if (user2.isEmpty) None else Some(user2)
      )
    }
  }

  def nextSupportUsers(
      date: DateTime,
      supportACL: SupportACL
  ): Option[(DateTime, (Option[String], Option[String]))] = {
    activeSupportUsers(date.plusDays(7), supportACL)
  }

  /** Returns the start and end times of future (after active and next) slots
    * for a given user
    * @return
    *   Tuple of the start date and the other user on the rota
    */
  def futureRotaSlotsForUser(
      date: DateTime,
      supportACL: SupportACL,
      user: String
  ): List[(DateTime, String)] = {
    supportACL.rota.toList
      .sortBy(_._1.getMillis)
      .collect {
        case (startTime, (user1, user2)) if user1 == user || user2 == user =>
          (startTime, if (user1 == user) user2 else user1)
      }
      .filter { case (start, _) =>
        start.isAfter(date.plus(supportACL.supportPeriod))
      }
  }

  /** Combine a user's permissions from all sources to work out everything they
    * can do.
    */
  def allUserPermissions(
      username: String,
      date: DateTime,
      acl: ACL,
      adminACL: ACL,
      supportACL: SupportACL
  ): Set[Permission] = {
    val access = userAccess(username, acl).getOrElse(Set.empty)
    val adminAccess = userAccess(username, adminACL).getOrElse(Set.empty)
    val supportAccess =
      userSupportAccess(username, date, supportACL).getOrElse(Set.empty)
    access ++ adminAccess ++ supportAccess
  }

  /** All the accounts this user has any access to.
    */
  def allUserAccounts(
      username: String,
      date: DateTime,
      acl: ACL,
      adminACL: ACL,
      supportACL: SupportACL
  ): Set[AwsAccount] = {
    allUserPermissions(username, date, acl, adminACL, supportACL).map(_.account)
  }

  /** Check if the provider user has been granted this permission.
    */
  def checkUserPermission(
      username: String,
      permissionId: String,
      date: DateTime,
      acl: ACL,
      adminACL: ACL,
      supportACL: SupportACL
  ): Option[Permission] = {
    allUserPermissions(username, date, acl, adminACL, supportACL).find(
      _.id == permissionId
    )
  }

  /** Check if the user has explicit access granted in the Access.scala file.
    */
  def hasExplicitAccess(
      username: String,
      permission: Permission,
      acl: ACL
  ): Boolean = {
    userAccess(username, acl).getOrElse(Set.empty).contains(permission)
  }

  /** Checks if a user has access to an account and returns a[ppropriate
    * permissions (if any).
    */
  def userAccountAccess(
      username: String,
      accountId: String,
      date: DateTime,
      acl: ACL,
      adminACL: ACL,
      supportACL: SupportACL
  ): Set[Permission] = {
    allUserPermissions(username, date, acl, adminACL, supportACL)
      .filter(_.account.authConfigKey == accountId)
  }
}
