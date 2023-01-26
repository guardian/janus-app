package logic

import awscala.dynamodbv2.Attribute
import com.gu.googleauth.UserIdentity
import com.gu.janus.model.{ACL, AuditLog, JanusAccessType, Permission}
import org.joda.time.{DateTime, DateTimeZone, Duration}
import logic.UserAccess.{hasExplicitAccess, username}
import play.api.Logging

object AuditTrail extends Logging {
  def createLog(
      user: UserIdentity,
      permission: Permission,
      janusAccessType: JanusAccessType,
      duration: Duration,
      acl: ACL
  ): AuditLog =
    AuditLog(
      permission.account.authConfigKey,
      username(user),
      DateTime.now(),
      duration,
      permission.label,
      janusAccessType,
      !hasExplicitAccess(username(user), permission, acl)
    )

  /** Converts an AuditLog into its DB representation.
    */
  def auditLogAttrs(auditLog: AuditLog): (String, Long, List[(String, Any)]) = {
    (
      auditLog.account,
      auditLog.dateTime.withZone(DateTimeZone.UTC).getMillis,
      List(
        "j_username" -> auditLog.username,
        "j_duration" -> auditLog.duration.getStandardSeconds,
        "j_accessLevel" -> auditLog.accessLevel,
        "j_accessType" -> auditLog.accessType.toString,
        "j_external" -> (if (auditLog.external) 1 else 0)
      )
    )
  }

  /** Extract nice error message from db conversion.
    */
  def errorStrings(
      error: Either[(String, Seq[Attribute]), AuditLog]
  ): Either[String, AuditLog] = {
    error.left.map(_._1)
  }

  /** Log detailed info for any DB result extraction errors.
    */
  def logDbResultErrs(
      attempt: Either[(String, Seq[Attribute]), AuditLog]
  ): Either[(String, Seq[Attribute]), AuditLog] = {
    attempt.left.foreach { case (message, attrs) =>
      val formattedAttrs = attrs.map { attr =>
        s"${attr.name}: ${attr.value.toString}"
      } mkString ", "
      logger.error(s"Failed to extract auditLog data $formattedAttrs")
    }
    attempt
  }

  /** (Attempt to) convert a database result into an audit log.
    */
  def auditLogFromAttrs(
      attrs: Seq[Attribute]
  ): Either[(String, Seq[Attribute]), AuditLog] = {
    for {
      account <- attrs
        .find("j_account" == _.name)
        .flatMap(_.value.s)
        .toRight("Could not extract account" -> attrs)
      username <- attrs
        .find("j_username" == _.name)
        .flatMap(_.value.s)
        .toRight("Could not extract username" -> attrs)
      dateTime <- attrs
        .find("j_timestamp" == _.name)
        .flatMap(_.value.n)
        .map(ts => new DateTime(ts.toLong, DateTimeZone.UTC))
        .toRight("Could not extract dateTime" -> attrs)
      duration <- attrs
        .find("j_duration" == _.name)
        .flatMap(_.value.n)
        .map(d => new Duration(d.toLong * 1000))
        .toRight("Could not extract duration" -> attrs)
      accessLevel <- attrs
        .find("j_accessLevel" == _.name)
        .flatMap(_.value.s)
        .toRight("Could not extract accessLevel" -> attrs)
      accessType <- attrs
        .find("j_accessType" == _.name)
        .flatMap(_.value.s)
        .flatMap(JanusAccessType.fromString)
        .toRight("Could not extract accessType" -> attrs)
      external <- attrs
        .find("j_external" == _.name)
        .flatMap(attr => attr.value.n)
        .flatMap {
          case "0" => Some(false)
          case "1" => Some(true)
          case _   => None
        }
        .toRight("Could not extract external" -> attrs)
    } yield AuditLog(
      account,
      username,
      dateTime,
      duration,
      accessLevel,
      accessType,
      external
    )
  }
}
