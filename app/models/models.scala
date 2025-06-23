package models

import com.gu.googleauth.UserIdentity
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, UNAUTHORIZED}
import play.api.libs.json.{Json, Writes}

enum AccountConfigStatus:
  case FederationConfigError(causedBy: Throwable)
  case ConfigSuccess
  case ConfigWarn(accounts: Set[String])
  case ConfigError(accounts: Set[String])

enum DisplayMode:
  case Normal, Spooky, Festive

case class JanusException(
    userMessage: String,
    engineerMessage: String,
    httpCode: Int,
    causedBy: Option[Throwable]
) extends Exception(engineerMessage, causedBy.orNull)

object JanusException {

  given janusExceptionWrites: Writes[JanusException] = Writes { exception =>
    Json.obj(
      "status" -> "error",
      "message" -> exception.userMessage,
      "httpCode" -> exception.httpCode
    )
  }

  given throwableWrites: Writes[Throwable] = Writes { throwable =>
    Json.obj(
      "status" -> "error",
      "message" -> throwable.getClass.getSimpleName
    )
  }

  def invalidRequest(
      user: UserIdentity,
      detail: String
  ): JanusException = JanusException(
    userMessage = s"Invalid request",
    engineerMessage = s"Invalid request for user ${user.username}: $detail",
    httpCode = BAD_REQUEST,
    causedBy = None
  )

  def missingFieldInRequest(
      user: UserIdentity,
      fieldName: String
  ): JanusException = JanusException(
    userMessage = s"Missing '$fieldName' field",
    engineerMessage =
      s"Missing '$fieldName' field in request body for user ${user.username}",
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

  def duplicatePasskeyNameFieldInRequest(
      user: UserIdentity,
      passkeyName: String
  ): JanusException = JanusException(
    userMessage = "passkeyName: already exists",
    engineerMessage =
      s"Passkey name '$passkeyName' already exists for user ${user.username}",
    httpCode = BAD_REQUEST,
    causedBy = None
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

  def noPasskeysRegistered(user: UserIdentity): JanusException =
    JanusException(
      userMessage =
        "No passkeys registered. Please register a passkey before attempting to authenticate.",
      engineerMessage =
        s"User ${user.username} attempted to authenticate but has no registered passkeys",
      httpCode = BAD_REQUEST,
      causedBy = None
    )
}
