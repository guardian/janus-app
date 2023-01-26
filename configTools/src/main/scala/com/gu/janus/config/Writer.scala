package com.gu.janus.config

import com.gu.janus.model.{JanusData, Permission}

object Writer {
  def toConfig(janusData: JanusData): String = {
    val uniquePermissions = allPermissions(janusData)
    stripWhitespace(
      templates.txt.janusData(janusData, uniquePermissions).toString
    )
  }

  private[config] def allPermissions(janusData: JanusData): Set[Permission] = {
    janusData.access.defaultPermissions ++
      janusData.access.userAccess.values.flatten.toSet ++
      janusData.admin.userAccess.values.flatten.toSet ++
      janusData.support.supportAccess
  }

  /** Twirl is designed for HTML, not plain text. As a result it's tricky to
    * control how whitespace (particularly newlines) get added to the file.
    *
    * This strips empty lines to make the file a bit more legible.
    */
  private def stripWhitespace(configContent: String): String = {
    configContent.split("\n").filter(_.trim.nonEmpty).mkString("\n")
  }
}
