package com.gu.janus

import com.gu.janus.JanusConfig.JanusConfigurationException
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class JanusConfigTest extends AnyFreeSpec with Matchers {
  "Can load a config file" in {
    noException should be thrownBy {
      JanusConfig.load("example.conf")
    }
  }

  "throws a Janus configuration exception if there is an error in the config" in {
    an[JanusConfigurationException] should be thrownBy {
      JanusConfig.load("invalid.conf")
    }
  }
}
