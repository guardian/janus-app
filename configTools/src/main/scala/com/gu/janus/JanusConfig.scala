package com.gu.janus

import java.io.File
import com.gu.janus.config.{Loader, Writer}
import com.gu.janus.model.*
import com.typesafe.config.{Config, ConfigException, ConfigFactory}
import cats.syntax.monoid.*

object JanusConfig:
  def load(dataFile: File): JanusData =
    val config = ConfigFactory.parseFile(dataFile)
    loadContents(config, s"file `${dataFile.getAbsolutePath}`")

  def load(resourceName: String): JanusData =
    val config = ConfigFactory.load(resourceName)
    loadContents(config, s"resource `$resourceName`")

  private def loadContents(config: Config, source: String): JanusData =
    Loader
      .fromConfig(config)
      .fold(
        errMsg =>
          throw JanusConfigurationException(
            s"Failed to load Janus Data from $source, $errMsg"
          ),
        identity
      )

  def write(janusData: JanusData): String =
    Writer.toConfig(janusData)

  def validate(janusData: JanusData): ValidationResult =
    Validation.policySizeChecks(janusData) |+| Validation.permissionUniqueness(janusData)

  class JanusConfigurationException(message: String)
    extends ConfigException(message)
