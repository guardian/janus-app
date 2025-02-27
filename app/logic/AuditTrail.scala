package logic

import com.gu.googleauth.UserIdentity
import com.gu.janus.model.{ACL, AuditLog, JanusAccessType, Permission}
import logic.UserAccess.{hasExplicitAccess, username}
import org.joda.time.{DateTime, DateTimeZone, Duration}
import play.api.Logging
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import scala.util.Try

object AuditTrail extends Logging {

  val tableName = "AuditTrail"
  val secondaryIndexName = "AuditTrailByUser"

  val partitionKeyName = "j_account"
  val sortKeyName = "j_timestamp"
  val userNameAttrName = "j_username"
  val durationAttrName = "j_duration"
  val accessLevelAttrName = "j_accessLevel"
  val accessTypeAttrName = "j_accessType"
  val isExternalAttrName = "j_external"

  /** Database item attributes for a single audit log entry. */
  case class AuditLogDbEntryAttrs(
      partitionKey: (String, AttributeValue),
      sortKey: (String, AttributeValue),
      userName: (String, AttributeValue),
      sessionDuration: (String, AttributeValue),
      accessLevel: (String, AttributeValue),
      accessType: (String, AttributeValue),
      isExternal: (String, AttributeValue)
  ) {
    val toMap: Map[String, AttributeValue] = Seq(
      partitionKey,
      sortKey,
      userName,
      sessionDuration,
      accessLevel,
      accessType,
      isExternal
    ).toMap
  }
  object AuditLogDbEntryAttrs {

    /** Converts an AuditLog into its DB representation. */
    def fromAuditLog(auditLog: AuditLog): AuditLogDbEntryAttrs =
      AuditLogDbEntryAttrs(
        partitionKey =
          partitionKeyName -> AttributeValue.fromS(auditLog.account),
        sortKey = {
          val accessTime =
            auditLog.dateTime.withZone(DateTimeZone.UTC).getMillis
          sortKeyName -> AttributeValue.fromN(accessTime.toString)
        },
        userName = userNameAttrName -> AttributeValue.fromS(auditLog.username),
        sessionDuration = durationAttrName -> AttributeValue.fromN(
          auditLog.duration.getStandardSeconds.toString
        ),
        accessLevel =
          accessLevelAttrName -> AttributeValue.fromS(auditLog.accessLevel),
        accessType = accessTypeAttrName -> AttributeValue.fromS(
          auditLog.accessType.toString
        ),
        isExternal = isExternalAttrName -> AttributeValue.fromN(
          (if (auditLog.external) 1 else 0).toString
        )
      )
  }

  /** Create an audit log entry for a user's access attempt. */
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
      account <- stringValue(attrs, partitionKeyName).toRight(
        "Could not extract account" -> attrs
      )
      username <- stringValue(attrs, userNameAttrName).toRight(
        "Could not extract username" -> attrs
      )
      dateTime <- longValue(attrs, sortKeyName)
        .map(ts => new DateTime(ts, DateTimeZone.UTC))
        .toRight("Could not extract dateTime" -> attrs)
      duration <- longValue(attrs, durationAttrName)
        .map(d => new Duration(d * 1000))
        .toRight("Could not extract duration" -> attrs)
      accessLevel <- stringValue(attrs, accessLevelAttrName).toRight(
        "Could not extract accessLevel" -> attrs
      )
      accessType <- stringValue(attrs, accessTypeAttrName)
        .flatMap(JanusAccessType.fromString)
        .toRight("Could not extract accessType" -> attrs)
      external <- longValue(attrs, isExternalAttrName)
        .flatMap {
          case 0 => Some(false)
          case 1 => Some(true)
          case _ => None
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

  private def stringValue(
      attrs: Map[String, AttributeValue],
      attrName: String
  ) =
    attrValue(attrs, attrName, v => Some(v.s()))

  private def longValue(attrs: Map[String, AttributeValue], attrName: String) =
    attrValue(attrs, attrName, v => Try(v.n().toLong).toOption)

  private def attrValue[A](
      attrs: Map[String, AttributeValue],
      attrName: String,
      result: AttributeValue => Option[A]
  ) =
    attrs
      .find { case (name, _) => attrName == name }
      .flatMap { case (_, value) => result(value) }
}
