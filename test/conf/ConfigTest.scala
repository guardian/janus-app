package conf

import com.gu.janus.model._
import com.typesafe.config.{ConfigException, ConfigFactory}
import fixtures.Fixtures._
import models.{ConfigError, ConfigSuccess, ConfigWarn, FederationConfigError}
import org.joda.time.Period
import org.scalatest.{FreeSpec, Matchers}
import play.api.Configuration

class ConfigTest extends FreeSpec with Matchers {

  "validateAccountConfig" - {

    val testJanusData = JanusData(
      accounts = Set.empty,
      ACL(Map.empty),
      ACL(Map.empty),
      SupportACL.create(Map.empty, Set.empty, Period.weeks(1))
    )

    "When config has no 'federation' key..." - {
      val emptyConfig = Configuration(ConfigFactory.parseString("{}"))

      "should succeed if the accounts list in janusData is empty" in {
        val janusData = testJanusData.copy(accounts = Set.empty)

        Config.validateAccountConfig(janusData, emptyConfig) shouldEqual ConfigSuccess
      }

      "should return an FederationConfigError if the janusData lists one or more accounts" in {
        val janusData = testJanusData.copy(accounts = Set(fooAct, barAct))
        val result = Config.validateAccountConfig(janusData, emptyConfig)

        result shouldBe a [FederationConfigError]
      }
    }

    "When config does have a 'federation' key..." - {

      "should succeed if both account lists are empty" in {
        val emptyConfig = Configuration(
          ConfigFactory.parseString(
            s"""
               | {
               |   federation {
               |   }
               | }
             """.stripMargin
          ))
        val janusData = testJanusData.copy(accounts = Set.empty)

        Config.validateAccountConfig(janusData, emptyConfig) shouldEqual ConfigSuccess
      }

      "should succeed if janusData and config contain the exact same accounts" in {
        val exampleConfig = Configuration(
          ConfigFactory.parseString(
            s"""
               | {
               |   federation {
               |     foo.aws.roleArn=role
               |     bar.aws.roleArn=role
               |     baz.aws.roleArn=role
               |   }
               | }
             """.stripMargin
          ))
        val janusData = testJanusData.copy(accounts = Set(fooAct, barAct, bazAct))

        Config.validateAccountConfig(janusData, exampleConfig) shouldEqual ConfigSuccess
      }

      "should return an error including the account missing from the config" in {
        val exampleConfig = Configuration(
          ConfigFactory.parseString(
            s"""
               | {
               |   federation {
               |     foo.aws.roleArn=role
               |     baz.aws.roleArn=role
               |   }
               | }
           """.stripMargin
          ))
        val janusData = testJanusData.copy(accounts = Set(fooAct, barAct, bazAct))

        Config.validateAccountConfig(janusData, exampleConfig) shouldEqual ConfigError(Set("bar"))
      }

      "should return an error including all of the accounts missing from the config" in {
        val exampleConfig = Configuration(
          ConfigFactory.parseString(
            s"""
               | {
               |   federation {
               |     foo.aws.roleArn=role
               |   }
               | }
           """.stripMargin
          ))
        val janusData = testJanusData.copy(accounts = Set(fooAct, barAct, bazAct))

        Config.validateAccountConfig(janusData, exampleConfig) shouldEqual ConfigError(Set("bar", "baz"))
      }

      "should warn if janusData is missing an account" in {
        val exampleConfig = Configuration(
          ConfigFactory.parseString(
            s"""
               | {
               |   federation {
               |     foo.aws.roleArn=role
               |     bar.aws.roleArn=role
               |     baz.aws.roleArn=role
               |   }
               | }
             """.stripMargin
          ))
        val janusData = testJanusData.copy(accounts = Set(fooAct, bazAct))

        Config.validateAccountConfig(janusData, exampleConfig) shouldEqual ConfigWarn(Set("bar"))
      }

      "should warn if janusData is missing more than one account" in {
        val exampleConfig = Configuration(
          ConfigFactory.parseString(
            s"""
               | {
               |   federation {
               |     foo.aws.roleArn=role
               |     bar.aws.roleArn=role
               |     baz.aws.roleArn=role
               |   }
               | }
             """.stripMargin
          ))
        val janusData = testJanusData.copy(accounts = Set(fooAct))

        Config.validateAccountConfig(janusData, exampleConfig) shouldEqual ConfigWarn(Set("bar", "baz"))
      }

    }
  }

}
