package models

import com.gu.googleauth.UserIdentity
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, UNAUTHORIZED}
import play.api.libs.json.{Json, Writes}

sealed trait AccountConfigStatus
case class FederationConfigError(causedBy: Throwable)
    extends AccountConfigStatus
case object ConfigSuccess extends AccountConfigStatus
case class ConfigWarn(accounts: Set[String]) extends AccountConfigStatus
case class ConfigError(accounts: Set[String]) extends AccountConfigStatus

sealed trait DisplayMode
object Normal extends DisplayMode
object Spooky extends DisplayMode
object Festive extends DisplayMode

case class JanusException(
    userMessage: String,
    engineerMessage: String,
    httpCode: Int,
    causedBy: Option[Throwable]
) extends Exception(engineerMessage, causedBy.orNull)

object JanusException {

  implicit val janusExceptionWrites: Writes[JanusException] = Writes {
    exception =>
      Json.obj(
        "status" -> "error",
        "message" -> exception.userMessage,
        "httpCode" -> exception.httpCode
      )
  }

  implicit val throwableWrites: Writes[Throwable] = Writes { throwable =>
    Json.obj(
      "status" -> "error",
      "message" -> throwable.getClass.getSimpleName
    )
  }

  def missingFieldInRequest(
      user: UserIdentity,
      fieldName: String
  ): JanusException = JanusException(
    userMessage = s"Missing $fieldName field",
    engineerMessage =
      s"Missing $fieldName in request body for user ${user.username}",
    httpCode = BAD_REQUEST,
    causedBy = None
  )

  def invalidFieldInRequest(
      user: UserIdentity,
      fieldName: String,
      cause: Throwable
  ): JanusException = JanusException(
    userMessage = s"Invalid $fieldName field",
    engineerMessage =
      s"Invalid $fieldName field in request body for user ${user.username}: ${cause.getMessage}",
    httpCode = BAD_REQUEST,
    causedBy = Some(cause)
  )

  def failedToLoadDbItem(
      user: UserIdentity,
      tableName: String,
      cause: Throwable
  ): JanusException = JanusException(
    userMessage = s"Failed to load item from $tableName table",
    engineerMessage =
      s"Failed to load item from $tableName table for user ${user.username}: ${cause.getMessage}",
    httpCode = INTERNAL_SERVER_ERROR,
    causedBy = Some(cause)
  )

  def failedToCreateDbItem(
      user: UserIdentity,
      tableName: String,
      cause: Throwable
  ): JanusException = JanusException(
    userMessage = s"Failed to store item in $tableName table",
    engineerMessage =
      s"Failed to store item in $tableName table for user ${user.username}: ${cause.getMessage}",
    httpCode = INTERNAL_SERVER_ERROR,
    causedBy = Some(cause)
  )

  def failedToUpdateDbItem(
      user: UserIdentity,
      tableName: String,
      attribName: String,
      cause: Throwable
  ): JanusException = JanusException(
    userMessage = s"Failed to update $tableName.$attribName",
    engineerMessage =
      s"Failed to update $tableName.$attribName for user ${user.username}: ${cause.getMessage}",
    httpCode = INTERNAL_SERVER_ERROR,
    causedBy = Some(cause)
  )

  def failedToDeleteDbItem(
      user: UserIdentity,
      tableName: String,
      cause: Throwable
  ): JanusException = JanusException(
    userMessage = s"Failed to delete from $tableName table",
    engineerMessage =
      s"Failed to delete from $tableName table for user ${user.username}: ${cause.getMessage}",
    httpCode = INTERNAL_SERVER_ERROR,
    causedBy = Some(cause)
  )

  def missingItemInDb(user: UserIdentity, tableName: String): JanusException =
    JanusException(
      userMessage = s"Item missing in $tableName table",
      engineerMessage =
        s"Item not found in $tableName table for user ${user.username}",
      httpCode = INTERNAL_SERVER_ERROR,
      causedBy = None
    )

  def authenticationFailure(
      user: UserIdentity,
      cause: Throwable
  ): JanusException =
    JanusException(
      userMessage = "Authentication failed",
      engineerMessage =
        s"Authentication failed for user ${user.username}: ${cause.getMessage}",
      httpCode = UNAUTHORIZED,
      causedBy = Some(cause)
    )
}
