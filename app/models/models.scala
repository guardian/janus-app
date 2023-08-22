package models

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
