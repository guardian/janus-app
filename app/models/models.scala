package models
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
}
