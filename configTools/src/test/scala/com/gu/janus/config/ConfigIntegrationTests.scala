package com.gu.janus.config

import com.typesafe.config.ConfigFactory
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import java.io.{File, PrintWriter}

import com.gu.janus.JanusConfig
import com.gu.janus.testutils.RightValues

class ConfigIntegrationTests
    extends AnyFreeSpec
    with Matchers
    with RightValues {
  "round trips" - {
    "the example janus data can be read, written and re-read" in {
      val testConfig = ConfigFactory.load("example.conf")
      val result = Loader.fromConfig(testConfig)
      val janusData = result.value
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
      val reloadedJanusData = JanusConfig.load(file)

      janusData.accounts shouldEqual reloadedJanusData.accounts
      janusData.access shouldEqual reloadedJanusData.access
      janusData.admin shouldEqual reloadedJanusData.admin
      janusData.support shouldEqual reloadedJanusData.support
      janusData.permissionsRepo shouldEqual reloadedJanusData.permissionsRepo

      file.delete()
    }

    "the example janus data that omits a permissions repo can be read, written and re-read" in {
      val testConfig =
        ConfigFactory.load("example-without-permissions-repo.conf")
      val result = Loader.fromConfig(testConfig)
      val janusData = result.value
      val content = Writer.toConfig(janusData)
      val file = File.createTempFile(
        "janus-config-integration-round-trip-test-no-perm-repo",
        ".conf"
      )
      file.deleteOnExit()
      new PrintWriter(file) {
        try {
          write(content)
        } finally {
          close()
        }
      }
      val reloadedJanusData = JanusConfig.load(file)

      janusData.accounts shouldEqual reloadedJanusData.accounts
      janusData.access shouldEqual reloadedJanusData.access
      janusData.admin shouldEqual reloadedJanusData.admin
      janusData.support shouldEqual reloadedJanusData.support
      janusData.permissionsRepo shouldEqual reloadedJanusData.permissionsRepo

      file.delete()
    }
  }

  "development helpers" - {
    // leave this ignored!
    // it's useful to switch `ignore` to `in` temporarily when working on the config format
    // run just this test with
    //     testOnly com.gu.janus.config.ConfigIntegrationTests -- -z "print the generated config file to the console for manual inspection"
    "print the generated config file to the console for manual inspection" ignore {
      val testConfig = ConfigFactory.load("example.conf")
      val result = Loader.fromConfig(testConfig)
      val janusData = result.value
      println(Writer.toConfig(janusData))
    }
  }
}
