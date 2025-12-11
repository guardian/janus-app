package com.gu.janus.config

import com.gu.janus.model.*
import com.gu.janus.testutils.HaveMatchers
import com.typesafe.config.ConfigFactory
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, OptionValues}

import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

class LoaderTest
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with EitherValues
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
            None,
            Some(
              List(
                """arn:aws:iam::aws:policy/AmazonEC2ReadOnlyAccess""",
                """arn:aws:iam::aws:policy/EC2InstanceConnect"""
              )
            ),
            false
          )
        )
      }

      "and extracts the ACL" in {
        val accounts = Loader.loadAccounts(testConfig).value
        val permissions = Loader.loadPermissions(testConfig, accounts).value
        val result = Loader.loadAccess(testConfig, permissions)
        val access = result.value
        access.userAccess
          .get("employee1")
          .value
          .permissions
          .map(_.id) shouldEqual Set(
          "website-developer"
        )
        access.userAccess
          .get("employee4")
          .value
          .permissions
          .map(_.id) shouldEqual Set(
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
        val websiteDeveloperPermission =
          userPermissions.permissions
            .find(p => p.id == "website-developer")
            .value
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
        val result = Loader.loadAccess(testConfig, permissions)
        val access = result.value
        val userPermissions = access.userAccess.get("employee3").value
        val websiteDeveloperPermission =
          userPermissions.permissions
            .find(p => p.id == "website-s3-manager")
            .value
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
        val result = Loader.loadAccess(testConfig, permissions)
        val access = result.value
        val userPermissions = access.userAccess.get("employee3").value
        val websiteDeveloperPermission =
          userPermissions.permissions
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
    }
  }

  "loadAdmin" - {
    "loads the example file's admin definition" in {
      val accounts = Loader.loadAccounts(testConfig).value
      val permissions = Loader.loadPermissions(testConfig, accounts).value
      val result = Loader.loadAdmin(testConfig, permissions)
      val adminAcl = result.value
      adminAcl.userAccess
        .get("employee1")
        .value
        .permissions
        .map(_.id) shouldEqual Set(
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

  "parseAclEntries" - {
    val testAccount = AwsAccount("Test Account", "test-account")
    val testAccount2 = AwsAccount("Test Account 2", "test-account-2")
    val testPermission = Permission(
      testAccount,
      "test-permission",
      "Test permission",
      Some("""{"Version":"2012-10-17","Statement":[]}"""),
      None,
      false
    )
    val anotherPermission = Permission(
      testAccount,
      "another-permission",
      "Another permission",
      None,
      Some(List("arn:aws:iam::aws:policy/ReadOnlyAccess")),
      false
    )
    val anotherPermission2 = Permission(
      testAccount2,
      "another-permission",
      "Another permission",
      None,
      Some(List("arn:aws:iam::aws:policy/ReadOnlyAccess")),
      false
    )
    val permissions = Set(testPermission, anotherPermission, anotherPermission2)

    "returns empty list for empty ACL" in {
      val result = Loader.parseAclEntries(Map.empty, permissions)
      result.value shouldEqual List.empty
    }

    "parses a single user with a single permission" - {
      val acl = Map(
        "user1" -> List(ConfiguredAclEntry("test-account", "test-permission"))
      )
      val result = Loader.parseAclEntries(acl, permissions)
      val entries = result.value

      "returns a single entry" in {
        entries should have size 1
      }

      "returns correct permissions" in {
        entries.head._2.permissions shouldEqual Set(testPermission)
      }

      "returns no roles" in {
        entries.head._2.roles shouldBe empty
      }
    }

    "parses a single user with multiple permissions" - {
      val acl = Map(
        "user1" -> List(
          ConfiguredAclEntry("test-account", "test-permission"),
          ConfiguredAclEntry("test-account-2", "another-permission")
        )
      )
      val result = Loader.parseAclEntries(acl, permissions)
      val entries = result.value

      "returns single entry" in {
        entries should have size 1
      }

      "returns correct permissions" in {
        entries.flatMap(_._2.permissions).toSet shouldEqual Set(
          testPermission,
          anotherPermission2
        )
      }
    }

    "parses multiple users" - {
      val acl = Map(
        "user1" -> List(ConfiguredAclEntry("test-account", "test-permission")),
        "user2" -> List(
          ConfiguredAclEntry("test-account", "another-permission")
        )
      )
      val result = Loader.parseAclEntries(acl, permissions)
      val entries = result.value

      "returns two entries" in {
        entries should have size 2
      }

      "returns correct user names" in {
        entries.map(_._1).toSet shouldEqual Set("user1", "user2")
      }

      "returns correct permissions" in {
        entries.flatMap(_._2.permissions).toSet shouldEqual Set(
          testPermission,
          anotherPermission
        )
      }
    }

    "parses a single role-based ACL entry" - {
      val acl = Map(
        "user1" -> List(ConfiguredRoleAclEntry("MyRole", "role-tag"))
      )
      val result = Loader.parseAclEntries(acl, permissions)
      val entries = result.value

      "returns single entry" in {
        entries should have size 1
      }

      "returns no permissions" in {
        entries.head._2.permissions shouldBe empty
      }

      "returns correct role" in {
        entries.head._2.roles shouldEqual Set(
          ProvisionedRole("MyRole", "role-tag")
        )
      }
    }

    "parses a multiple-role-based ACL entry" - {
      val acl = Map(
        "user1" -> List(
          ConfiguredRoleAclEntry("MyRole", "role-tag"),
          ConfiguredRoleAclEntry("MyRole2", "role-tag-2")
        )
      )
      val result = Loader.parseAclEntries(acl, permissions)
      val entries = result.value

      "returns single entry" in {
        entries should have size 1
      }

      "returns no permissions" in {
        entries.head._2.permissions shouldBe empty
      }

      "returns correct roles" in {
        entries.head._2.roles shouldEqual Set(
          ProvisionedRole("MyRole", "role-tag"),
          ProvisionedRole("MyRole2", "role-tag-2")
        )
      }
    }

    "parses mixed permission and role entries for same user" - {
      val acl = Map(
        "user1" -> List(
          ConfiguredAclEntry("test-account", "test-permission"),
          ConfiguredRoleAclEntry("MyRole", "role-tag")
        )
      )
      val result = Loader.parseAclEntries(acl, permissions)
      val entries = result.value

      "returns single entry" in {
        entries should have size 1
      }

      "returns correct permissions" in {
        entries.flatMap(_._2.permissions).toSet shouldEqual Set(
          testPermission
        )
      }

      "returns correct roles" in {
        entries.flatMap(_._2.roles).toSet shouldEqual Set(
          ProvisionedRole("MyRole", "role-tag")
        )
      }
    }

    "fails when permission doesn't exist" - {
      val acl = Map(
        "user1" -> List(
          ConfiguredAclEntry("test-account", "nonexistent-permission")
        )
      )
      val result = Loader.parseAclEntries(acl, permissions)

      "returns failure value" in {
        result.isLeft shouldBe true
      }

      "failure value is correct" in {
        result.left.value should include("nonexistent-permission")
      }
    }

    "fails when account doesn't exist" - {
      val acl = Map(
        "user1" -> List(
          ConfiguredAclEntry("nonexistent-account", "test-permission")
        )
      )
      val result = Loader.parseAclEntries(acl, permissions)

      "returns failure value" in {
        result.isLeft shouldBe true
      }

      "failure value is correct" in {
        result.left.value should include("nonexistent-account")
      }
    }
  }
}
