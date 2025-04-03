package models

import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR}

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

sealed trait JanusException extends Exception {
  def userMessage: String
  def engineerMessage: String
  def httpCode: Int
}
sealed trait JanusExceptionWithCause extends JanusException {
  def cause: Throwable
}
case class BadArgumentException(
    userMessage: String,
    engineerMessage: String,
    cause: Throwable
) extends JanusExceptionWithCause {
  val httpCode: Int = BAD_REQUEST
}
case class AwsCallException(
    userMessage: String,
    engineerMessage: String,
    cause: Throwable
) extends JanusExceptionWithCause {
  val httpCode: Int = INTERNAL_SERVER_ERROR
}
case class PasskeyVerificationException(
    userMessage: String,
    engineerMessage: String,
    cause: Throwable
) extends JanusExceptionWithCause {
  val httpCode: Int = BAD_REQUEST
}
case class NotFoundException(userMessage: String, engineerMessage: String)
    extends JanusException {
  val httpCode: Int = BAD_REQUEST
}
