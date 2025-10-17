package com.gu.janus

import java.io.File

import com.gu.janus.config.{Loader, Writer}
import com.gu.janus.model._
import com.typesafe.config.{Config, ConfigException, ConfigFactory}
import Validation.validationResultMonoid
import cats.syntax.monoid._

object JanusConfig {
  def load(dataFile: File): JanusData = {
    val config = ConfigFactory.parseFile(dataFile)
    loadContents(config, s"file `${dataFile.getAbsolutePath}`")
  }

  def load(resourceName: String): JanusData = {
    val config = ConfigFactory.load(resourceName)
    loadContents(config, s"resource `$resourceName`")
  }

  private def loadContents(config: Config, source: String): JanusData = {
    Loader
      .fromConfig(config)
      .fold(
        { errMsg =>
          throw new JanusConfigurationException(
            s"Failed to load Janus Data from $source, $errMsg"
          )
        },
        identity
      )
  }

  def write(janusData: JanusData): String = {
    Writer.toConfig(janusData)
  }

  def validate(janusData: JanusData): ValidationResult = {
    Validation.policySizeChecks(janusData) |+| Validation.permissionUniqueness(
      janusData
    )
  }

  def allPermissions(janusData: JanusData): Set[Permission] = {
    janusData.access.defaultPermissions ++
      allAclPermissions(janusData.access) ++
      allAclPermissions(janusData.admin) ++
      janusData.support.supportAccess
  }

  def allRoles(janusData: JanusData): Set[Role] = {
    (for {
      acl <- List(janusData.access, janusData.admin)
      (_, ACLEntry(permissions, roles)) <- acl.userAccess
      role <- roles
    } yield role).toSet
  }

  private[janus] def allAclPermissions(acl: ACL): Set[Permission] = {
    val userAccessPermissions =
      acl.userAccess.values.flatMap(_.permissions).toSet
    val rolePermissions =
      acl.userAccess.values.flatMap(_.roles.flatMap(_.permissions)).toSet
    val defaultPermissions = acl.defaultPermissions

    userAccessPermissions ++ rolePermissions ++ defaultPermissions
  }

  class JanusConfigurationException(message: String)
      extends ConfigException(message)
}
