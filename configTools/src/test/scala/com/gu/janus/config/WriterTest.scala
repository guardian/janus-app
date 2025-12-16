package com.gu.janus.config

import com.gu.janus.model.*
import com.gu.janus.policy.Iam.Effect.Allow
import com.gu.janus.policy.Iam.{Action, Policy, Resource, Statement}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class WriterTest extends AnyFreeSpec with Matchers {
  val account1 = AwsAccount("Test 1", "test1")
  val account2 = AwsAccount("Test 2", "test2")
  val simpleStatement = Statement(
    Allow,
    Seq(Action("sts:GetCallerIdentity")),
    Seq(Resource("*"))
  )
  val simplePolicy = Policy(Seq(simpleStatement))

  "toConfig" - {
    "includes the permissionsRepo" in {
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map.empty, Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty),
        Some("https://example.com/")
      )
      Writer.toConfig(janusData) should include(
        """permissionsRepo = "https://example.com/""""
      )
    }

    "excludes permissionsRepo entry if it is empty" in {
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map.empty, Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty),
        None
      )
      Writer.toConfig(janusData) should not include "permissionsRepo"
    }

    "includes the inline policy for a permission" in {
      val permission = Permission(
        account1,
        "perm1",
        "Test permission",
        simplePolicy,
        false
      )
      val janusData = JanusData(
        Set(account1),
        access =
          ACL(Map("user1" -> ACLEntry(Set(permission), Set.empty)), Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty),
        None
      )
      Writer.toConfig(janusData) should include(
        """{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["sts:GetCallerIdentity"],"Resource":["*"]}]}"""
      )
    }

    "includes the managed policy ARNs for a permission" in {
      val permission = Permission.fromManagedPolicyArns(
        account1,
        "perm1",
        "Test permission",
        List("arn:aws:iam::aws:policy/ReadOnlyAccess"),
        false
      )
      val janusData = JanusData(
        Set(account1),
        access =
          ACL(Map("user1" -> ACLEntry(Set(permission), Set.empty)), Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty),
        None
      )
      Writer.toConfig(janusData) should include(
        """arn:aws:iam::aws:policy/ReadOnlyAccess"""
      )
    }

    "access section" - {
      val permission = Permission(
        account1,
        "accessPerm",
        "Access permission",
        simplePolicy,
        false
      )
      val provisionedRole = ProvisionedRole("TestRole", "test-role-tag")

      "includes user in ACL" in {
        val janusData = JanusData(
          Set(account1),
          access = ACL(
            Map("testuser" -> ACLEntry(Set(permission), Set.empty)),
            Set.empty
          ),
          admin = ACL(Map.empty, Set.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        Writer.toConfig(janusData) should include(""""testuser"""")
      }

      "includes provisioned role name in ACL entry" in {
        val janusData = JanusData(
          Set(account1),
          access = ACL(
            Map("testuser" -> ACLEntry(Set.empty, Set(provisionedRole))),
            Set.empty
          ),
          admin = ACL(Map.empty, Set.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        Writer.toConfig(janusData) should include(
          s"provisionedRoleName = \"\"\"TestRole\"\"\""
        )
      }

      "includes provisioned role iamRoleTag in ACL entry" in {
        val janusData = JanusData(
          Set(account1),
          access = ACL(
            Map("testuser" -> ACLEntry(Set.empty, Set(provisionedRole))),
            Set.empty
          ),
          admin = ACL(Map.empty, Set.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        Writer.toConfig(janusData) should include(
          """iamRoleTag = "test-role-tag""""
        )
      }

      "includes multiple provisioned roles for a single user" in {
        val role1 = ProvisionedRole("Role1", "role-1-tag")
        val role2 = ProvisionedRole("Role2", "role-2-tag")
        val janusData = JanusData(
          Set(account1),
          access = ACL(
            Map("testuser" -> ACLEntry(Set.empty, Set(role1, role2))),
            Set.empty
          ),
          admin = ACL(Map.empty, Set.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        val config = Writer.toConfig(janusData)
        config should include(s"provisionedRoleName = \"\"\"Role1\"\"\"")
        config should include(s"provisionedRoleName = \"\"\"Role2\"\"\"")
      }

      "includes multiple permissions and multiple roles for a single user" in {
        val perm1 =
          Permission(account1, "perm1", "Permission 1", simplePolicy, false)
        val perm2 =
          Permission(account1, "perm2", "Permission 2", simplePolicy, false)
        val role1 = ProvisionedRole("Role1", "role-1-tag")
        val role2 = ProvisionedRole("Role2", "role-2-tag")
        val janusData = JanusData(
          Set(account1),
          access = ACL(
            Map("testuser" -> ACLEntry(Set(perm1, perm2), Set(role1, role2))),
            Set.empty
          ),
          admin = ACL(Map.empty, Set.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        val config = Writer.toConfig(janusData)
        config should include("""label = "perm1"""")
        config should include("""label = "perm2"""")
        config should include(s"provisionedRoleName = \"\"\"Role1\"\"\"")
        config should include(s"provisionedRoleName = \"\"\"Role2\"\"\"")
      }
    }

    "admin section" - {
      val permission = Permission(
        account1,
        "adminPerm",
        "Admin permission",
        simplePolicy,
        false
      )
      val provisionedRole = ProvisionedRole("AdminRole", "admin-role-tag")

      "includes user in admin ACL" in {
        val janusData = JanusData(
          Set(account1),
          access = ACL(Map.empty, Set.empty),
          admin = ACL(
            Map("adminuser" -> ACLEntry(Set(permission), Set.empty)),
            Set.empty
          ),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        Writer.toConfig(janusData) should include(""""adminuser"""")
      }

      "includes provisioned role in admin ACL entry" in {
        val janusData = JanusData(
          Set(account1),
          access = ACL(Map.empty, Set.empty),
          admin = ACL(
            Map("adminuser" -> ACLEntry(Set.empty, Set(provisionedRole))),
            Set.empty
          ),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        Writer.toConfig(janusData) should include(
          s"provisionedRoleName = \"\"\"AdminRole\"\"\""
        )
      }

      "includes provisioned role iamRoleTag in admin ACL entry" in {
        val janusData = JanusData(
          Set(account1),
          access = ACL(Map.empty, Set.empty),
          admin = ACL(
            Map("adminuser" -> ACLEntry(Set.empty, Set(provisionedRole))),
            Set.empty
          ),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        Writer.toConfig(janusData) should include(
          """iamRoleTag = "admin-role-tag""""
        )
      }

      "includes multiple provisioned roles for a single user" in {
        val role1 = ProvisionedRole("AdminRole1", "admin-role-1-tag")
        val role2 = ProvisionedRole("AdminRole2", "admin-role-2-tag")
        val janusData = JanusData(
          Set(account1),
          access = ACL(Map.empty, Set.empty),
          admin = ACL(
            Map("adminuser" -> ACLEntry(Set.empty, Set(role1, role2))),
            Set.empty
          ),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        val config = Writer.toConfig(janusData)
        config should include(s"provisionedRoleName = \"\"\"AdminRole1\"\"\"")
        config should include(s"provisionedRoleName = \"\"\"AdminRole2\"\"\"")
      }

      "includes multiple permissions and multiple roles for a single user" in {
        val perm1 = Permission(
          account1,
          "adminPerm1",
          "Admin Permission 1",
          simplePolicy,
          false
        )
        val perm2 = Permission(
          account1,
          "adminPerm2",
          "Admin Permission 2",
          simplePolicy,
          false
        )
        val role1 = ProvisionedRole("AdminRole1", "admin-role-1-tag")
        val role2 = ProvisionedRole("AdminRole2", "admin-role-2-tag")
        val janusData = JanusData(
          Set(account1),
          access = ACL(Map.empty, Set.empty),
          admin = ACL(
            Map("adminuser" -> ACLEntry(Set(perm1, perm2), Set(role1, role2))),
            Set.empty
          ),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        val config = Writer.toConfig(janusData)
        config should include("""label = "adminPerm1"""")
        config should include("""label = "adminPerm2"""")
        config should include(s"provisionedRoleName = \"\"\"AdminRole1\"\"\"")
        config should include(s"provisionedRoleName = \"\"\"AdminRole2\"\"\"")
      }
    }
  }
}
