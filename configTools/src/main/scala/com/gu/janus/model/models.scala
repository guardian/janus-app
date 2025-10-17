package com.gu.janus.model

import cats.Monoid
import com.gu.janus.policy.Iam.Policy
import io.circe.syntax.EncoderOps

import java.time.{Duration, Instant}

case class JanusData(
    accounts: Set[AwsAccount],
    access: ACL,
    admin: ACL,
    support: SupportACL,
    permissionsRepo: Option[String]
)

case class ACL(
    userAccess: Map[String, ACLEntry],
    defaultPermissions: Set[Permission] = Set.empty
)
case class ACLEntry(
    permissions: Set[Permission],
    roles: Set[Role]
)
object ACLEntry {
  given Monoid[ACLEntry] with {
    def empty: ACLEntry = ACLEntry(Set.empty, Set.empty)
    def combine(a1: ACLEntry, a2: ACLEntry): ACLEntry =
      ACLEntry(
        a1.permissions ++ a2.permissions,
        a1.roles ++ a2.roles
      )
  }
}
case class SupportACL private (
    rota: Map[Instant, (String, String)],
    supportAccess: Set[Permission],
    supportPeriod: Duration
)
object SupportACL {

  /** Normalise the data on creation so it is impossible to have a bad
    * representation.
    */
  def create(
      rota: Map[Instant, (String, String)],
      supportAccess: Set[Permission],
      supportPeriod: Duration
  ): SupportACL = new SupportACL(
    rota,
    supportAccess,
    supportPeriod
  )
}

case class AwsAccount(
    /** The display name for the AWS account - will appear on the Janus UI
      */
    name: String,

    /** This value is used for three purposes:
      *
      *   - the configuration key used to look up the role to assume, to
      *     authenticate into this account
      *   - forms part of the permission key used to uniquely identify a
      *     permission (you'll see this in the URL of Janus console and
      *     credentials requests)
      *
      * and most obviously to end users...
      *
      *   - the profile name for created Janus CLI sessions in that account
      *     (assuming you use the standard mechanism of populating an AWS
      *     profile with the provided name)
      *
      * NOTE: This last example can be optionally overridden by a permission, in
      * which case the permission's label will be used as the profile name.
      */
    authConfigKey: String
)

case class AwsAccountAccess(
    awsAccount: AwsAccount,
    permissions: List[Permission],
    isFavourite: Boolean
)

/** Represents the access granted to an AWS session, via the console login or as
  * temporary credentials.
  *
  * Permissions are associated with an AWS account and are made up of:
  *   - metadata (`label` and `description`)
  *   - the policy(s) that control the session's access
  *   - flags (`shortTerm` and `sessionType`) that modify Janus' behaviour
  *
  * @param account
  *   The AWS Account that this permission accesses.
  * @param label
  *   Internal ID used for permission lookup, also used as the credential
  *   profile name if `overrideProfileName` is true. This should be called
  *   something like `key`, but the name remains for legacy reasons.
  * @param description
  *   Human-readable name, used to describe the permission to a user in the UI.
  * @param policy
  *   An AWS IAM policy that's passed inline to set the permissions for the
  *   assumed session. This is the standard way to define permissions in Janus.
  * @param managedPolicyArns
  *   An AWS-managed IAM policy that specifies the access for the resulting
  *   session.
  * @param shortTerm
  *   Permissions that bestow significant levels of access should set this flag
  *   to limit session length. This reduces the risk of admin access and
  *   discourages the use of admin permissions for day-to-day work.
  * @param sessionType
  *   Permissions usually use the account's authConfigKey as the AWS profile
  *   name. This is useful as a replacement for a user's own permanent
  *   credentials but because you can only have one active session at a time
  *   under a given profile name this is not a good fit for carefully tailored
  *   permissions like you might see when running a service locally. 'User'
  *   permissions keep Janus' existing behaviour and are the default, while
  *   'Workload' permission isolate the AWS profile name by instead using the
  *   permission label.
  */
case class Permission(
    account: AwsAccount,
    label: String,
    description: String,
    policy: Option[String],
    managedPolicyArns: Option[List[String]],
    shortTerm: Boolean,
    sessionType: SessionType
) {
  val id = s"${account.authConfigKey}-$label"

  val credentialsProfile: String =
    sessionType match {
      case SessionType.User     => account.authConfigKey
      case SessionType.Workload => label
    }

  override def toString: String = s"Permission<$id>"
}
object Permission {

  /** Creates a permission using an inline policy document.
    *
    * This is the normal way to define permissions in Janus so that we have an
    * immutable record of exactly what access each user has, tied to the audit
    * trail of approvals.
    */
  def apply(
      account: AwsAccount,
      label: String,
      description: String,
      policy: Policy,
      shortTerm: Boolean = false,
      sessionType: SessionType = SessionType.User
  ): Permission = {
    Permission(
      account,
      label,
      description,
      Some(policy.asJson.noSpaces),
      None,
      shortTerm,
      sessionType
    )
  }

  /** Create a permission that's based on managed IAM policies instead of
    * providing a policy document.
    *
    * These should usually be AWS-managed policies, and this provides two useful
    * features:
    *   - set up service-specific permissions that are automatically kept up to
    *     date with AWS changes
    *   - bypass the size limit on inline policies for complex permissions (e.g.
    *     global read access)
    */
  def fromManagedPolicyArns(
      account: AwsAccount,
      label: String,
      description: String,
      managedPolicyArns: List[String],
      shortTerm: Boolean = false,
      sessionType: SessionType = SessionType.User
  ): Permission = {
    Permission(
      account,
      label,
      description,
      None,
      if (managedPolicyArns.nonEmpty) Some(managedPolicyArns) else None,
      shortTerm,
      sessionType
    )
  }

  /** Creates a permission that combines managed policy ARNs with an inline
    * policy document. More information on each of these options is above.
    *
    * This combination allows us to take managed policies as a baseline and
    * customise the resulting permission with an inline policy document.
    */
  def withManagedPolicyArns(
      account: AwsAccount,
      label: String,
      description: String,
      inlinePolicy: Policy,
      managedPolicyArns: List[String],
      shortTerm: Boolean = false,
      sessionType: SessionType = SessionType.User
  ): Permission = {
    Permission(
      account,
      label,
      description,
      Some(inlinePolicy.asJson.noSpaces),
      if (managedPolicyArns.nonEmpty) Some(managedPolicyArns) else None,
      shortTerm,
      sessionType
    )
  }
}

sealed trait SessionType
object SessionType {
  case object User extends SessionType
  case object Workload extends SessionType
}

case class Role(
    name: String,
    permissions: Set[Permission]
)
object Role {
  def apply(
      name: String,
      permissions: Permission*
  ): Role = Role(name, permissions.toSet)
}

sealed abstract class JanusAccessType(override val toString: String)
object JCredentials extends JanusAccessType("credentials")
object JConsole extends JanusAccessType("console")
object JanusAccessType {
  def fromString(string: String): Option[JanusAccessType] = {
    if ("credentials" == string) Some(JCredentials)
    else if ("console" == string) Some(JConsole)
    else None
  }
}

case class AuditLog(
    account: String,
    username: String,
    instant: Instant,
    duration: Duration,
    accessLevel: String,
    accessType: JanusAccessType,
    external: Boolean
)
