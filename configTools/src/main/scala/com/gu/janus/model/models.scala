package com.gu.janus.model

import awscala.Policy
import com.gu.janus.policy.PolicyJson
import org.joda.time._

case class JanusData(
    accounts: Set[AwsAccount],
    access: ACL,
    admin: ACL,
    support: SupportACL,
    permissionsRepo: Option[String]
)

case class ACL(
    userAccess: Map[String, Set[Permission]],
    defaultPermissions: Set[Permission] = Set.empty
)
case class SupportACL private (
    rota: Map[DateTime, (String, String)],
    supportAccess: Set[Permission],
    supportPeriod: Seconds
)
object SupportACL {

  /** Normalise the data on creation so it is impossible to have a bad
    * representation.
    */
  def create(
      rota: Map[DateTime, (String, String)],
      supportAccess: Set[Permission],
      supportPeriod: Period
  ): SupportACL = {
    val rotaWithNormalisedTimezones = rota.map { case (dt, a) =>
      dt.withZone(DateTimeZone.UTC) -> a
    }
    new SupportACL(
      rotaWithNormalisedTimezones,
      supportAccess,
      supportPeriod.toStandardSeconds
    )
  }
}

case class AwsAccount(
    name: String,
    authConfigKey: String
)
case class AwsAccountAccess(
    awsAccount: AwsAccount,
    permissions: List[Permission],
    isFavourite: Boolean
)

case class Permission(
    account: AwsAccount,
    label: String,
    description: String,
    policy: String,
    shortTerm: Boolean
) {
  val id = s"${account.authConfigKey}-$label"

  override def toString: String = s"Permission<$id>"
}
object Permission {
  def apply(
      account: AwsAccount,
      label: String,
      description: String,
      policy: Policy,
      shortTerm: Boolean = false
  ): Permission = {
    val simplifiedPolicy = PolicyJson.stripSids(policy.asJson)
    Permission(account, label, description, simplifiedPolicy, shortTerm)
  }
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
    dateTime: DateTime,
    duration: Duration,
    accessLevel: String,
    accessType: JanusAccessType,
    external: Boolean
)

case class AccountOwners(
    admins: List[String],
    devs: List[String],
    others: List[String]
) {
  val isEmpty = admins.isEmpty && devs.isEmpty && others.isEmpty
}
object AccountOwners {
  def empty = AccountOwners(Nil, Nil, Nil)
}
