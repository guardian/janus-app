package logic

import com.gu.googleauth.UserIdentity
import com.gu.janus.model.*
import logic.DeveloperPolicies.toPermission
import logic.SupportUserAccess.userSupportAccess
import models.*

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
    Option.when(access.nonEmpty)(access.view.mapValues(toAccountAccess).toMap)
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
    Option.when(access.nonEmpty)(access.view.mapValues(toAccountAccess).toMap)
  }

  /** Flattens into a structure that's useful where we only need to know about
    * types of permissions and we don't need information about which ACL
    * permission comes from.
    */
  private def toAccountAccess(access: SourcedAccountAccess) =
    AccountAccess(
      permissions = access.internal.permissions ++
        access.admin.permissions ++
        access.support.permissions,
      developerPolicies = access.internal.developerPolicies ++
        access.admin.developerPolicies ++
        access.support.developerPolicies
    )

  /** Checks if the username is explicitly mentioned in the provided ACL.
    */
  def hasAccess(username: String, acl: ACL): Boolean = {
    acl.userAccess.contains(username)
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
  ): Option[(Permission, AccessSource)] =
    totalUserAccess(
      username,
      Some(janusData.access),
      Some(janusData.admin),
      Some((janusData.support, date)),
      developerPolicies
    ).valuesIterator.flatMap { access =>
      access.bySource.flatMap((aa, src) =>
        (aa.permissions ++ aa.developerPolicies.map(toPermission))
          .find(_.id == permissionId)
          .map((_, src))
      )
    }.nextOption

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
        .map((acl, date) =>
          val perms =
            userSupportAccess(username, date, acl).getOrElse(Set.empty)
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
}
