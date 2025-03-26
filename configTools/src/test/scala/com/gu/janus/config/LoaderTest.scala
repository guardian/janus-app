package com.gu.janus.config

import com.gu.janus.model.{AwsAccount, Permission}
import com.gu.janus.testutils.{HaveMatchers, RightValues}
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.ZoneOffset.UTC
import java.time.{Duration, ZonedDateTime}

class LoaderTest
    extends AnyFreeSpec
    with Matchers
    with RightValues
    with OptionValues
    with HaveMatchers {
  val testConfig = ConfigFactory.load("example.conf")
  val testConfigWithoutPermissionsRepo =
    ConfigFactory.load("example-without-permissions-repo.conf")

  "fromConfig" - {
    "parses the full example" in {
      val result = Loader.fromConfig(testConfig)
      val janusData = result.value
      // TODO: check the data here as well
      janusData.permissionsRepo shouldEqual Some("https://example.com/")
    }

    "parses an example without a permissions repo" in {
      val result = Loader.fromConfig(testConfigWithoutPermissionsRepo)
      val janusData = result.value
      // TODO: check the data here as well
      janusData.permissionsRepo shouldEqual None
    }
  }

  "loadPermissionsRepo" - {
    "loads the example file's repo" in {
      val result = Loader.loadPermissionsRepo(testConfig)
      val repoUrl = result.value.value
      repoUrl shouldEqual "https://example.com/"
    }

    "loads example with no permissions repository" in {
      val result = Loader.loadPermissionsRepo(testConfigWithoutPermissionsRepo)
      val permissionsRepo = result.value
      permissionsRepo shouldEqual None
    }
  }

  "loadAccounts" - {
    "loads the example file's accounts" in {
      val result = Loader.loadAccounts(testConfig)
      val accounts = result.value
      accounts.map(_.authConfigKey) shouldEqual Set(
        "main-account",
        "website",
        "aws-test-account"
      )
    }
  }

  "loadPermissions" - {
    "loads the example file's permissions" in {
      val accounts = Loader.loadAccounts(testConfig).value
      val result = Loader.loadPermissions(testConfig, accounts)
      val permissions = result.value
      permissions.map(_.id) should contain("main-account-test-permission")
    }
  }

  "loadAccess" - {
    "loads the example file's access definition" - {
      "and extracts the default permissions" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val result = Loader.loadAccess(testConfig, permissions)
        val access = result.value
        access.defaultPermissions shouldEqual Set(
          Permission(
            AwsAccount("Testing account", "aws-test-account"),
            "default-test",
            "Default test access",
            Some(""),
            Some(List("""arn:aws:iam::aws:policy/AmazonEC2ReadOnlyAccess""", """arn:aws:iam::aws:policy/EC2InstanceConnect""")),
            false
          )
        )
      }

      "and extracts the ACL" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val result = Loader.loadAccess(testConfig, permissions)
        val access = result.value
        access.userAccess.get("employee1").value.map(_.id) shouldEqual Set(
          "website-developer"
        )
        access.userAccess.get("employee4").value.map(_.id) shouldEqual Set(
          "website-s3-manager",
          "aws-test-account-developer"
        )
      }

      "properly extracts a standard inline-policy permission" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val result = Loader.loadAccess(testConfig, permissions)
        val access = result.value
        val userPermissions = access.userAccess.get("employee1").value
        val websiteDeveloperPermission = userPermissions.find(p => p.id == "website-developer").value
        websiteDeveloperPermission should have(
          "policy" as Some("""{"Version":"2012-10-17","Statement":[{"Sid":"1","Effect":"Allow","Action":["s3:*"],"Resource":["*"]}]}"""),
          "managedPolicyArns" as None,
          "shortTerm" as false,
          "description" as "Developer access"
        )
      }

      "properly extracts a policy with managed ARNs" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val result = Loader.loadAccess(testConfig, permissions)
        val access = result.value
        val userPermissions = access.userAccess.get("employee3").value
        val websiteDeveloperPermission = userPermissions.find(p => p.id == "website-s3-manager").value
        websiteDeveloperPermission should have(
          "managedPolicyArns" as Some(List("""arn:aws:iam::aws:policy/AmazonS3FullAccess""")),
          "policy" as None,
          "shortTerm" as false,
          "description" as "Read and write access to S3"
        )
      }
    }
  }

  "loadAdmin" - {
    "loads the example file's admin definition" in {
      val accounts = Loader.loadAccounts(testConfig).value
      val permissions = Loader.loadPermissions(testConfig, accounts).value
      val result = Loader.loadAdmin(testConfig, permissions)
      val adminAcl = result.value
      adminAcl.userAccess.get("employee1").value.map(_.id) shouldEqual Set(
        "website-admin"
      )
    }
  }

  "loadSupport" - {
    "loads the example file's support definition" - {
      "extracts the support permissions" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val result = Loader.loadSupport(testConfig, permissions)
        val supportAcl = result.value
        supportAcl.supportAccess.map(_.id) shouldEqual Set(
          "website-developer",
          "aws-test-account-developer"
        )
      }

      "extracts the support period" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val result = Loader.loadSupport(testConfig, permissions)
        val supportAcl = result.value
        supportAcl.supportPeriod shouldEqual Duration.ofSeconds(604800L)
      }

      "extracts the rota" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val result = Loader.loadSupport(testConfig, permissions)
        val supportAcl = result.value
        supportAcl.rota shouldEqual Map(
          ZonedDateTime
            .of(
              2018,
              12,
              27,
              11,
              0,
              0,
              0,
              UTC
            )
            .toInstant -> ("employee1", "employee2"),
          ZonedDateTime
            .of(
              2019,
              1,
              3,
              11,
              0,
              0,
              0,
              UTC
            )
            .toInstant -> ("employee2", "employee4"),
          ZonedDateTime
            .of(
              2019,
              1,
              10,
              11,
              0,
              0,
              0,
              UTC
            )
            .toInstant -> ("employee2", "employee5")
        )
      }
    }
  }
}
