package logic

import com.gu.googleauth.UserIdentity
import com.gu.janus.model.*
import logic.DeveloperPolicies.toPermission
import logic.SupportUserAccess.userSupportAccess
import models.{AccountAccess, DeveloperPolicy}

import java.time.Instant

object UserAccess {

  /** Extract the username from the pan-domain user.
    */
  def username(user: UserIdentity): String =
    user.email.split("@").head.toLowerCase

  /** A user's access in the given ACL. Note that default permissions are
    * available for anyone mentioned in the ACL.
    *
    * If the user appears in the ACL, this will return an [[AccountAccess]] per
    * [[AwsAccount]], preserving the distinction between static Janus
    * permissions and dynamically-loaded developer policies. There will be a key
    * for any AWS account to which the user has access.
    */
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

  /** Checks if the username is explicitly mentioned in the provided ACL.
    */
  def hasAccess(username: String, acl: ACL): Boolean = {
    acl.userAccess.keySet.contains(username)
  }

  /** Returns the set of developer policy grants explicitly assigned to the user
    * in the ACL, if any.
    */
  def policyGrantsForUser(
      username: String,
      acl: ACL
  ): Set[DeveloperPolicyGrant] =
    acl.userAccess
      .get(username)
      .map(_.policyGrants)
      .getOrElse(Set.empty)

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
      explicitAcl: ACL,
      adminAcl: ACL,
      supportACL: SupportACL,
      developerPolicies: Set[DeveloperPolicy]
  ): Option[(Permission, Boolean)] = {
    val explicit = userPermissions(username, explicitAcl, developerPolicies)
    val admin = userPermissions(username, adminAcl, developerPolicies)
    val support =
      userSupportAccess(username, date, supportACL).getOrElse(Set.empty)
    val allPerms = explicit ++ admin ++ support
    allPerms
      .find(_.id == permissionId)
      .map(permission => (permission, explicit.contains(permission)))
  }

  private[logic] def userPermissions(
      username: String,
      acl: ACL,
      developerPolicies: Set[DeveloperPolicy]
  ): Set[Permission] =
    userAccess(username, acl, developerPolicies)
      .map(
        _.values
          .flatMap(aa =>
            aa.permissions ++ aa.developerPolicies.map(toPermission)
          )
          .toSet
      )
      .getOrElse(Set.empty)
}
