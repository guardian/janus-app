package logic

import com.gu.googleauth.UserIdentity
import com.gu.janus.model.{
  ACL,
  AuditLog,
  JanusAccessType,
  Permission,
  PermissionType
}
import com.gu.janus.model.PermissionType.AccountPermission
import logic.UserAccess.username
import play.api.Logging
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import java.time.{Duration, Instant}
import scala.util.Try

object AuditTrail extends Logging {

  val tableName = "AuditTrail"
  val secondaryIndexName = "AuditTrailByUser"

  /* Database item attributes.
   * Named with a 'j_' prefix to avoid conflicts with DynamoDB reserved keywords.
   */
  // AWS account name - used as the partition key for the table
  val accountPartitionKeyName = "j_account"
  // Timestamp of the access attempt - used as the sort key for the table and for its secondary index
  val timestampSortKeyName = "j_timestamp"
  // User name - indexed attribute
  val userNameAttrName = "j_username"
  // TTL of the granted session, in seconds
  val durationAttrName = "j_duration"
  // Access role requested
  val accessLevelAttrName = "j_accessLevel"
  // Whether access request was for AWS console or credentials for local use
  val accessTypeAttrName = "j_accessType"
  val isExternalAttrName = "j_external"
  val permissionTypeAttrName = "j_permissionType"

  /** Database item attributes for a single audit log entry. */
  case class AuditLogDbEntryAttrs(
      partitionKey: (String, AttributeValue),
      sortKey: (String, AttributeValue),
      userName: (String, AttributeValue),
      sessionDuration: (String, AttributeValue),
      accessLevel: (String, AttributeValue),
      accessType: (String, AttributeValue),
      isExternal: (String, AttributeValue),
      permissionType: (String, AttributeValue)
  ) {
    val toMap: Map[String, AttributeValue] = Seq(
      partitionKey,
      sortKey,
      userName,
      sessionDuration,
      accessLevel,
      accessType,
      isExternal,
      permissionType
    ).toMap
  }
  object AuditLogDbEntryAttrs {

    /** Converts an AuditLog into its DB representation. */
    def fromAuditLog(auditLog: AuditLog): AuditLogDbEntryAttrs =
      AuditLogDbEntryAttrs(
        partitionKey =
          accountPartitionKeyName -> AttributeValue.fromS(auditLog.account),
        sortKey = {
          val accessTime = auditLog.instant.toEpochMilli
          timestampSortKeyName -> AttributeValue.fromN(accessTime.toString)
        },
        userName = userNameAttrName -> AttributeValue.fromS(auditLog.username),
        sessionDuration = durationAttrName -> AttributeValue.fromN(
          auditLog.duration.getSeconds.toString
        ),
        accessLevel =
          accessLevelAttrName -> AttributeValue.fromS(auditLog.accessLevel),
        accessType = accessTypeAttrName -> AttributeValue.fromS(
          auditLog.accessType.toString
        ),
        isExternal = isExternalAttrName -> AttributeValue.fromN(
          (if (auditLog.external) 1 else 0).toString
        ),
        permissionType = permissionTypeAttrName -> AttributeValue.fromS(
          auditLog.permissionType.serialised
        )
      )
  }

  /** Create an audit log entry for a user's access attempt. */
  def createLog(
      user: UserIdentity,
      permission: Permission,
      janusAccessType: JanusAccessType,
      duration: Duration,
      acl: ACL,
      hasExplicitAccess: Boolean,
      permissionType: PermissionType
  ): AuditLog =
    AuditLog(
      permission.account.authConfigKey,
      username(user),
      Instant.now(),
      duration,
      permission.label,
      janusAccessType,
      !hasExplicitAccess,
      permissionType
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
      account <- stringValue(attrs, accountPartitionKeyName).toRight(
        "Could not extract account" -> attrs
      )
      username <- stringValue(attrs, userNameAttrName).toRight(
        "Could not extract username" -> attrs
      )
      dateTime <- longValue(attrs, timestampSortKeyName)
        .map(epochMilli => Instant.ofEpochMilli(epochMilli))
        .toRight("Could not extract dateTime" -> attrs)
      duration <- longValue(attrs, durationAttrName)
        .map(Duration.ofSeconds)
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
    } yield {
      val permissionType = stringValue(attrs, permissionTypeAttrName)
        .flatMap(PermissionType.fromString)
        .getOrElse(AccountPermission)
      AuditLog(
        account,
        username,
        dateTime,
        duration,
        accessLevel,
        accessType,
        external,
        permissionType
      )
    }
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
