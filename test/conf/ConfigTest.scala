package conf

import com.gu.janus.model._
import com.typesafe.config.ConfigFactory
import fixtures.Fixtures._
import models.AccountConfigStatus
import models.AccountConfigStatus.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration

import java.time.Duration
import scala.util.Success

class ConfigTest extends AnyFreeSpec with Matchers {
  "roleArn" - {
    "is extracted from the application configuration for that account" in {
      val config = Configuration(
        ConfigFactory.parseString(
          """{
            |  federation {
            |    foo.aws.roleArn="arn:aws:iam::123456789012:role/test-role"
            |  }
            |}
            |""".stripMargin
        )
      )
      Config.roleArn(
        "foo",
        config
      ) shouldEqual "arn:aws:iam::123456789012:role/test-role"
    }
  }

  "accountNumber" - {
    "extracts the account number from the configured role ARN for the account" in {
      val config = Configuration(
        ConfigFactory.parseString(
          """{
            |  federation {
            |    foo.aws.roleArn="arn:aws:iam::123456789012:role/test-role"
            |  }
            |}
            |""".stripMargin
        )
      )
      Config.accountNumber("foo", config) shouldEqual Success("123456789012")
    }

    "returns a failure if the account is not configured" in {
      val config = Configuration(
        ConfigFactory.parseString(
          """{
            |  federation {
            |    foo.aws.roleArn="arn:aws:iam::123456789012:role/test-role"
            |  }
            |}
            |""".stripMargin
        )
      )
      Config.accountNumber("bar", config).isFailure shouldBe true
    }

    "returns a failure if the account number cannot be extracted from the role ARN" in {
      val config = Configuration(
        ConfigFactory.parseString(
          """{
            |  federation {
            |    foo.aws.roleArn="not a role ARN like we expect"
            |  }
            |}
            |""".stripMargin
        )
      )
      Config.accountNumber("foo", config).isFailure shouldBe true
    }
  }

  "validateAccountConfig" - {
    val testJanusData = JanusData(
      accounts = Set.empty,
      ACL(Map.empty),
      ACL(Map.empty),
      SupportACL.create(Map.empty, Set.empty),
      Some("https://example.com/")
    )

    "When config has no 'federation' key..." - {
      val emptyConfig = Configuration(ConfigFactory.parseString("{}"))

      "should succeed if the accounts list in janusData is empty" in {
        val janusData = testJanusData.copy(accounts = Set.empty)

        Config.validateAccountConfig(
          janusData,
          emptyConfig
        ) shouldEqual ConfigSuccess
      }

      "should return an FederationConfigError if the janusData lists one or more accounts" in {
        val janusData = testJanusData.copy(accounts = Set(fooAct, barAct))
        val result = Config.validateAccountConfig(janusData, emptyConfig)

        result shouldBe a[FederationConfigError]
      }
    }

    "When config does have a 'federation' key..." - {

      "should succeed if both account lists are empty" in {
        val emptyConfig = Configuration(
          ConfigFactory.parseString(
            s"""{
               |  federation {
               |  }
               |}
             """.stripMargin
          )
        )
        val janusData = testJanusData.copy(accounts = Set.empty)

        Config.validateAccountConfig(
          janusData,
          emptyConfig
        ) shouldEqual ConfigSuccess
      }

      "should succeed if janusData and config contain the exact same accounts" in {
        val exampleConfig = Configuration(
          ConfigFactory.parseString(
            s"""{
               |  federation {
               |    foo.aws.roleArn=role
               |    bar.aws.roleArn=role
               |    baz.aws.roleArn=role
               |  }
               |}
               |""".stripMargin
          )
        )
        val janusData =
          testJanusData.copy(accounts = Set(fooAct, barAct, bazAct))

        Config.validateAccountConfig(
          janusData,
          exampleConfig
        ) shouldEqual ConfigSuccess
      }

      "should return an error including the account missing from the config" in {
        val exampleConfig = Configuration(
          ConfigFactory.parseString(
            s"""{
               |  federation {
               |    foo.aws.roleArn=role
               |    baz.aws.roleArn=role
               |  }
               |}
           """.stripMargin
          )
        )
        val janusData =
          testJanusData.copy(accounts = Set(fooAct, barAct, bazAct))

        Config.validateAccountConfig(
          janusData,
          exampleConfig
        ) shouldEqual ConfigError(Set("bar"))
      }

      "should return an error including all of the accounts missing from the config" in {
        val exampleConfig = Configuration(
          ConfigFactory.parseString(
            s"""{
               |  federation {
               |    foo.aws.roleArn=role
               |  }
               |}
           """.stripMargin
          )
        )
        val janusData =
          testJanusData.copy(accounts = Set(fooAct, barAct, bazAct))

        Config.validateAccountConfig(
          janusData,
          exampleConfig
        ) shouldEqual ConfigError(Set("bar", "baz"))
      }

      "should warn if janusData is missing an account" in {
        val exampleConfig = Configuration(
          ConfigFactory.parseString(
            s"""{
               |  federation {
               |    foo.aws.roleArn=role
               |    bar.aws.roleArn=role
               |    baz.aws.roleArn=role
               |  }
               |}
               |""".stripMargin
          )
        )
        val janusData = testJanusData.copy(accounts = Set(fooAct, bazAct))

        Config.validateAccountConfig(
          janusData,
          exampleConfig
        ) shouldEqual ConfigWarn(Set("bar"))
      }

      "should warn if janusData is missing more than one account" in {
        val exampleConfig = Configuration(
          ConfigFactory.parseString(
            s"""{
               |  federation {
               |    foo.aws.roleArn=role
               |    bar.aws.roleArn=role
               |    baz.aws.roleArn=role
               |  }
               |}
               |""".stripMargin
          )
        )
        val janusData = testJanusData.copy(accounts = Set(fooAct))

        Config.validateAccountConfig(
          janusData,
          exampleConfig
        ) shouldEqual ConfigWarn(Set("bar", "baz"))
      }

    }
  }

}
