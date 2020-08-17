package com.gu.janus.config

import com.gu.janus.model.{AwsAccount, Permission}
import com.typesafe.config.ConfigFactory
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone, Period}
import org.scalatest.{EitherValues, FreeSpec, Matchers, OptionValues}


class LoaderTest extends FreeSpec with Matchers with EitherValues with OptionValues {
  val testConfig = ConfigFactory.load("example.conf")
  val testConfigWithoutPermissionsRepo = ConfigFactory.load("example-without-permissions-repo.conf")

  "fromConfig" - {
    "parses the full example" in {
      val result = Loader.fromConfig(testConfig)
      val janusData = result.right.value
      // TODO: check the data here as well
      janusData.permissionsRepo shouldEqual Some("https://example.com/")
    }

    "parses an example without a permissions repo" in {
      val result = Loader.fromConfig(testConfigWithoutPermissionsRepo)
      val janusData = result.right.value
      // TODO: check the data here as well
      janusData.permissionsRepo shouldEqual None
    }
  }

  "loadPermissionsRepo" - {
    "loads the example file's repo" in {
      val result = Loader.loadPermissionsRepo(testConfig)
      val repoUrl = result.right.value.value
      repoUrl shouldEqual "https://example.com/"
    }

    "loads example with no permissions repository" in {
      val result = Loader.loadPermissionsRepo(testConfigWithoutPermissionsRepo)
      val permissionsRepo = result.right.value
      permissionsRepo shouldEqual None
    }
  }

  "loadAccounts" - {
    "loads the example file's accounts" in {
      val result = Loader.loadAccounts(testConfig)
      val accounts = result.right.value
      accounts.map(_.authConfigKey) shouldEqual Set(
        "main-account",
        "website",
        "aws-test-account"
      )
    }
  }

  "loadPermissions" - {
    "loads the example file's permissions" in {
      val accounts = Loader.loadAccounts(testConfig).right.value
      val result = Loader.loadPermissions(testConfig, accounts)
      val permissions = result.right.value
      permissions.map(_.id) should contain("main-account-test-permission")
    }
  }

  "loadAccess" - {
    "loads the example file's access definition" - {
      "and extracts the default permissions" in {
        val accounts = Loader.loadAccounts(testConfig).right.value
        val permissions = Loader.loadPermissions(testConfig, accounts).right.value
        val result = Loader.loadAccess(testConfig, permissions)
        val access = result.right.value
        access.defaultPermissions shouldEqual Set(
          Permission(
            AwsAccount("Testing account", "aws-test-account"),
            "default-test", "Default test access", "", false
          )
        )
      }

      "and extracts the ACL" in {
        val accounts = Loader.loadAccounts(testConfig).right.value
        val permissions = Loader.loadPermissions(testConfig, accounts).right.value
        val result = Loader.loadAccess(testConfig, permissions)
        val access = result.right.value
        access.userAccess.get("employee1").value.map(_.id) shouldEqual Set("website-developer")
        access.userAccess.get("employee4").value.map(_.id) shouldEqual Set("website-s3-manager", "aws-test-account-developer")
      }
    }
  }

  "loadAdmin" - {
    "loads the example file's admin definition" in {
      val accounts = Loader.loadAccounts(testConfig).right.value
      val permissions = Loader.loadPermissions(testConfig, accounts).right.value
      val result = Loader.loadAdmin(testConfig, permissions)
      val adminAcl = result.right.value
      adminAcl.userAccess.get("employee1").value.map(_.id) shouldEqual Set("website-admin")
    }
  }

  "loadSupport" - {
    "loads the example file's support definition" - {
      "extracts the support permissions" in {
        val accounts = Loader.loadAccounts(testConfig).right.value
        val permissions = Loader.loadPermissions(testConfig, accounts).right.value
        val result = Loader.loadSupport(testConfig, permissions)
        val fmt = ISODateTimeFormat.dateTime()
        val supportAcl = result.right.value
        supportAcl.supportAccess.map(_.id) shouldEqual Set(
          "website-developer",
          "aws-test-account-developer"
        )
      }

      "extracts the support period" in {
        val accounts = Loader.loadAccounts(testConfig).right.value
        val permissions = Loader.loadPermissions(testConfig, accounts).right.value
        val result = Loader.loadSupport(testConfig, permissions)
        val supportAcl = result.right.value
        supportAcl.supportPeriod shouldEqual Period.seconds(604800).toStandardSeconds
      }

      "extracts the rota" in {
        val accounts = Loader.loadAccounts(testConfig).right.value
        val permissions = Loader.loadPermissions(testConfig, accounts).right.value
        val result = Loader.loadSupport(testConfig, permissions)
        val supportAcl = result.right.value
        supportAcl.rota shouldEqual Map(
          new DateTime(2018, 12, 27, 11, 0, 0, DateTimeZone.UTC) -> ("employee1", "employee2"),
          new DateTime(2019,  1,  3, 11, 0, 0, DateTimeZone.UTC) -> ("employee2", "employee4"),
          new DateTime(2019,  1, 10, 11, 0, 0, DateTimeZone.UTC) -> ("employee2", "employee5")
        )
      }
    }
  }
}
