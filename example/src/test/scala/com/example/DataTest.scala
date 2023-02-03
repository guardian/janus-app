package com.example

import java.io.{File, PrintWriter}

import com.gu.janus.JanusConfig
import com.gu.janus.config.Writer
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class DataTest extends AnyFreeSpec with Matchers {
  "The Janus data should not contain any errors" in {
    JanusConfig.validate(Data.janusData).errors shouldBe empty
  }

  "Check for potential problems with the generated data by going to and from the config representation" in {
    // write data to config representation
    val janusData = Data.janusData
    val content = Writer.toConfig(janusData)
    val file =
      File.createTempFile("janus-config-integration-round-trip-test", ".conf")
    file.deleteOnExit()
    new PrintWriter(file) {
      try {
        write(content)
      } finally {
        close()
      }
    }

    // check equality of data after re-loading from config
    val reloadedJanusData = JanusConfig.load(file)

    janusData.accounts shouldEqual reloadedJanusData.accounts

    janusData.access.userAccess shouldEqual reloadedJanusData.access.userAccess
    janusData.access.defaultPermissions shouldEqual reloadedJanusData.access.defaultPermissions

    janusData.support.supportPeriod shouldEqual reloadedJanusData.support.supportPeriod
    janusData.support.supportAccess shouldEqual reloadedJanusData.support.supportAccess
    janusData.support.rota shouldEqual reloadedJanusData.support.rota

    janusData.admin shouldEqual reloadedJanusData.admin

    file.delete()
  }
}
