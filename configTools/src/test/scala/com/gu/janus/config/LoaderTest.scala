package com.gu.janus.config

import com.gu.janus.model.SessionType.{User, Workload}
import com.gu.janus.model.{AwsAccount, Permission, SessionType, ACLEntry}
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

    "a permission lacking the sessionType field should be loaded as a User permission" in {
      val accounts = Loader.loadAccounts(testConfig).value
      val result = Loader.loadPermissions(testConfig, accounts)
      val permissions = result.value
      val legacyPermissionSessionType = permissions
        .find(_.id == "website-s3-manager")
        .value
        .sessionType
      legacyPermissionSessionType shouldEqual SessionType.User
    }
  }

  "loadAccess" - {
    "loads the example file's access definition" - {
      "and extracts the default permissions" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val roles = Loader.loadRoles(testConfig, permissions).value
        val result = Loader.loadAccess(testConfig, permissions, roles)
        val access = result.value
        access.defaultPermissions shouldEqual Set(
          Permission(
            AwsAccount("Testing account", "aws-test-account"),
            label = "default-test",
            description = "Default test access",
            policy = None,
            managedPolicyArns = Some(
              List(
                """arn:aws:iam::aws:policy/AmazonEC2ReadOnlyAccess""",
                """arn:aws:iam::aws:policy/EC2InstanceConnect"""
              )
            ),
            shortTerm = false,
            sessionType = User
          )
        )
      }

      "and extracts the ACL" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val roles = Loader.loadRoles(testConfig, permissions).value
        val acl = Loader.loadAccess(testConfig, permissions, roles).value
        val ACLEntry(employee2Permissions, employee2Roles) =
          acl.userAccess.get("employee1").value
        employee2Permissions.map(_.id) shouldEqual Set(
          "website-developer"
        )
        val ACLEntry(employee4Permissions, employee4Roles) =
          acl.userAccess.get("employee4").value
        employee4Permissions.map(_.id) shouldEqual Set(
          "website-s3-manager",
          "aws-test-account-developer"
        )
      }

      "properly extracts a standard inline-policy permission" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val roles = Loader.loadRoles(testConfig, permissions).value
        val result = Loader.loadAccess(testConfig, permissions, roles)
        val access = result.value
        val ACLEntry(userPermissions, userRoles) =
          access.userAccess.get("employee1").value
        val websiteDeveloperPermission =
          userPermissions.find(p => p.id == "website-developer").value
        websiteDeveloperPermission should have(
          "description" as "Developer access",
          "policy" as Some(
            """{"Version":"2012-10-17","Statement":[{"Sid":"1","Effect":"Allow","Action":["s3:*"],"Resource":["*"]}]}"""
          ),
          "managedPolicyArns" as None,
          "shortTerm" as false
        )
      }

      "properly extracts a permission with managed ARNs" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val roles = Loader.loadRoles(testConfig, permissions).value
        val result = Loader.loadAccess(testConfig, permissions, roles)
        val access = result.value
        val ACLEntry(userPermissions, userRoles) =
          access.userAccess.get("employee3").value
        val websiteDeveloperPermission =
          userPermissions.find(p => p.id == "website-s3-manager").value
        websiteDeveloperPermission should have(
          "description" as "Read and write access to S3",
          "managedPolicyArns" as Some(
            List("""arn:aws:iam::aws:policy/AmazonS3FullAccess""")
          ),
          "policy" as None,
          "shortTerm" as false
        )
      }

      "properly extracts a permission with an inline policy document and managed policy ARNs" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val roles = Loader.loadRoles(testConfig, permissions).value
        val result = Loader.loadAccess(testConfig, permissions, roles)
        val access = result.value
        val ACLEntry(userPermissions, userRoles) =
          access.userAccess.get("employee3").value
        val websiteDeveloperPermission =
          userPermissions
            .find(p => p.id == "aws-test-account-hybrid-permission")
            .value
        websiteDeveloperPermission should have(
          "description" as "Managed and inline access control",
          "managedPolicyArns" as Some(
            List("""arn:aws:iam::aws:policy/ReadOnlyAccess""")
          ),
          "policy" as Some(
            """{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["sts:GetCallerIdentity"],"Resource":["*"]}]}"""
          ),
          "shortTerm" as false
        )
      }

      "properly extracts a permission with session type User" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val roles = Loader.loadRoles(testConfig, permissions).value
        val result = Loader.loadAccess(testConfig, permissions, roles)
        val access = result.value
        val ACLEntry(userPermissions, userRoles) =
          access.userAccess.get("employee1").value
        val websiteDeveloperPermission =
          userPermissions.find(p => p.id == "website-developer").value
        websiteDeveloperPermission.sessionType shouldEqual User
      }

      "properly extracts a permission with session type Workload" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val roles = Loader.loadRoles(testConfig, permissions).value
        val result = Loader.loadAccess(testConfig, permissions, roles)
        val access = result.value
        val ACLEntry(userPermissions, userRoles) =
          access.userAccess.get("employee2").value
        val websiteDeveloperPermission =
          userPermissions.find(p => p.id == "website-web-workload").value
        websiteDeveloperPermission.sessionType shouldEqual Workload
      }
    }
  }

  "loadAdmin" - {
    "loads the example file's admin definition" in {
      val accounts = Loader.loadAccounts(testConfig).value
      val permissions = Loader.loadPermissions(testConfig, accounts).value
      val adminAcl = Loader.loadAdmin(testConfig, permissions).value
      val ACLEntry(adminPermissions, _) =
        adminAcl.userAccess.get("employee1").value
      adminPermissions.map(_.id) shouldEqual Set(
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
