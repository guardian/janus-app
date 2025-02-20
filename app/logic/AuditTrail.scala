package logic

import com.gu.googleauth.UserIdentity
import com.gu.janus.model.{ACL, AuditLog, JanusAccessType, Permission}
import logic.UserAccess.{hasExplicitAccess, username}
import org.joda.time.{DateTime, DateTimeZone, Duration}
import play.api.Logging
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

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
      error: Either[(String, Map[String, AttributeValue]), AuditLog]
  ): Either[String, AuditLog] = {
    error.left.map(_._1)
  }

  /** Log detailed info for any DB result extraction errors.
    */
  def logDbResultErrs(
      attempt: Either[(String, Map[String, AttributeValue]), AuditLog]
  ): Either[(String, Map[String, AttributeValue]), AuditLog] = {
    attempt.left.foreach { case (_, attrs) =>
      val formattedAttrs = attrs.map { case (name, value) =>
        s"$name: ${value.toString}"
      } mkString ", "
      logger.error(s"Failed to extract auditLog data $formattedAttrs")
    }
    attempt
  }

  /** (Attempt to) convert a database result into an audit log.
    */
  def auditLogFromAttrs(
      attrs: Map[String, AttributeValue]
  ): Either[(String, Map[String, AttributeValue]), AuditLog] = {
    for {
      account <- attrs
        .find { case (name, _) => "j_account" == name }
        .flatMap { case (_, value) => Some(value.s()) }
        .toRight("Could not extract account" -> attrs)
      username <- attrs
        .find { case (name, _) => "j_username" == name }
        .flatMap { case (_, value) => Some(value.s()) }
        .toRight("Could not extract username" -> attrs)
      dateTime <- attrs
        .find { case (name, _) => "j_timestamp" == name }
        .flatMap { case (_, value) => Some(value.n()) }
        .map(ts => new DateTime(ts.toLong, DateTimeZone.UTC))
        .toRight("Could not extract dateTime" -> attrs)
      duration <- attrs
        .find { case (name, _) => "j_duration" == name }
        .flatMap { case (_, value) => Some(value.n()) }
        .map(d => new Duration(d.toLong * 1000))
        .toRight("Could not extract duration" -> attrs)
      accessLevel <- attrs
        .find { case (name, _) => "j_accessLevel" == name }
        .flatMap { case (_, value) => Some(value.s()) }
        .toRight("Could not extract accessLevel" -> attrs)
      accessType <- attrs
        .find { case (name, _) => "j_accessType" == name }
        .flatMap { case (_, value) => Some(value.s()) }
        .flatMap(JanusAccessType.fromString)
        .toRight("Could not extract accessType" -> attrs)
      external <- attrs
        .find { case (name, _) => "j_external" == name }
        .flatMap { case (_, value) => Some(value.n()) }
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
