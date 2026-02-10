package com.gu.janus.model

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

/** Access available to a single user. */
case class ACLEntry(
    permissions: Set[Permission],
    roles: Set[ProvisionedRole]
)

case class SupportACL private (
    rota: Map[Instant, (String, String)],
    supportAccess: Set[Permission]
)
object SupportACL {

  /** Normalise the data on creation so it is impossible to have a bad
    * representation.
    */
  def create(
      rota: Map[Instant, (String, String)],
      supportAccess: Set[Permission]
  ): SupportACL = new SupportACL(
    rota,
    supportAccess
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
      */
    authConfigKey: String
)

case class Permission(
    account: AwsAccount,
    label: String,
    description: String,
    policy: Option[String],
    managedPolicyArns: Option[List[String]],
    shortTerm: Boolean
) {
  val id = s"${account.authConfigKey}-$label"

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
      shortTerm: Boolean = false
  ): Permission = {
    Permission(
      account,
      label,
      description,
      Some(policy.asJson.noSpaces),
      None,
      shortTerm
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
      shortTerm: Boolean = false
  ): Permission = {
    Permission(
      account,
      label,
      description,
      None,
      if (managedPolicyArns.nonEmpty) Some(managedPolicyArns) else None,
      shortTerm
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
      shortTerm: Boolean = false
  ): Permission = {
    Permission(
      account,
      label,
      description,
      Some(inlinePolicy.asJson.noSpaces),
      if (managedPolicyArns.nonEmpty) Some(managedPolicyArns) else None,
      shortTerm
    )
  }

  def allPermissions(janusData: JanusData): Set[Permission] = {
    def perms(
        access: Map[String, ACLEntry]
    ): Set[Permission] =
      access.values.flatMap(_.permissions).toSet

    janusData.access.defaultPermissions ++
      perms(janusData.access.userAccess) ++
      perms(janusData.admin.userAccess) ++
      janusData.support.supportAccess
  }
}

/** A set of provisioned IAM roles that Janus can discover by tag lookup. */
case class ProvisionedRole(
    /** A friendly name to identify this in a UI or elsewhere. */
    name: String,

    /** Hook that will allow us to discover the IAM roles included in this set.
      * Each relevant role will be found by a tag identifying it as a Janus role
      * and a tag that matches this value.
      */
    iamRoleTag: String
)

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
