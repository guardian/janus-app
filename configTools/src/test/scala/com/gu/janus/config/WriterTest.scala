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
      val grant =
        DeveloperPolicyGrant(
          "Test Grant Name",
          "test-grant-id",
          shortTerm = false
        )

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

      "includes DeveloperPolicyGrant name in ACL entry" in {
        val janusData = JanusData(
          Set(account1),
          access = ACL(
            Map("testuser" -> ACLEntry(Set.empty, Set(grant))),
            Set.empty
          ),
          admin = ACL(Map.empty, Set.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        Writer.toConfig(janusData) should include(
          "grantName = \"\"\"Test Grant Name\"\"\", grantId = \"test-grant-id\", shortTerm = false"
        )
      }

      "includes DeveloperPolicyGrant id in ACL entry" in {
        val janusData = JanusData(
          Set(account1),
          access = ACL(
            Map("testuser" -> ACLEntry(Set.empty, Set(grant))),
            Set.empty
          ),
          admin = ACL(Map.empty, Set.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        Writer.toConfig(janusData) should include(
          """grantId = "test-grant-id""""
        )
      }

      "includes multiple DeveloperPolicyGrants for a single user" - {
        val grant1 =
          DeveloperPolicyGrant("Grant1", "grant-1-id", shortTerm = false)
        val grant2 =
          DeveloperPolicyGrant("Grant2", "grant-2-id", shortTerm = true)
        val janusData = JanusData(
          Set(account1),
          access = ACL(
            Map("testuser" -> ACLEntry(Set.empty, Set(grant1, grant2))),
            Set.empty
          ),
          admin = ACL(Map.empty, Set.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        val config = Writer.toConfig(janusData)

        "includes Grant1" in {
          config should include(
            "grantName = \"\"\"Grant1\"\"\", grantId = \"grant-1-id\", shortTerm = false"
          )
        }

        "includes Grant2" in {
          config should include(
            "grantName = \"\"\"Grant2\"\"\", grantId = \"grant-2-id\", shortTerm = true"
          )
        }
      }

      "includes multiple permissions and multiple policy grants for a single user" - {
        val perm1 =
          Permission(account1, "perm1", "Permission 1", simplePolicy, false)
        val perm2 =
          Permission(account1, "perm2", "Permission 2", simplePolicy, false)
        val grant1 =
          DeveloperPolicyGrant("Grant1", "grant-1-id", shortTerm = true)
        val grant2 =
          DeveloperPolicyGrant("Grant2", "grant-2-id", shortTerm = false)
        val janusData = JanusData(
          Set(account1),
          access = ACL(
            Map("testuser" -> ACLEntry(Set(perm1, perm2), Set(grant1, grant2))),
            Set.empty
          ),
          admin = ACL(Map.empty, Set.empty),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        val config = Writer.toConfig(janusData)

        "includes perm1" in {
          config should include("""label = "perm1"""")
        }

        "includes perm2" in {
          config should include("""label = "perm2"""")
        }

        "includes Grant1" in {
          config should include(
            "grantName = \"\"\"Grant1\"\"\", grantId = \"grant-1-id\", shortTerm = true"
          )
        }

        "includes Grant2" in {
          config should include(
            "grantName = \"\"\"Grant2\"\"\", grantId = \"grant-2-id\", shortTerm = false"
          )
        }
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
      val grant =
        DeveloperPolicyGrant("AdminGrant", "admin-grant-id", shortTerm = false)

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

      "includes DeveloperPolicyGrant in admin ACL entry" in {
        val janusData = JanusData(
          Set(account1),
          access = ACL(Map.empty, Set.empty),
          admin = ACL(
            Map("adminuser" -> ACLEntry(Set.empty, Set(grant))),
            Set.empty
          ),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        Writer.toConfig(janusData) should include(
          "grantName = \"\"\"AdminGrant\"\"\", grantId = \"admin-grant-id\", shortTerm = false"
        )
      }

      "includes DeveloperPolicyGrant id in admin ACL entry" in {
        val janusData = JanusData(
          Set(account1),
          access = ACL(Map.empty, Set.empty),
          admin = ACL(
            Map("adminuser" -> ACLEntry(Set.empty, Set(grant))),
            Set.empty
          ),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        Writer.toConfig(janusData) should include(
          """grantId = "admin-grant-id""""
        )
      }

      "includes multiple DeveloperPolicyGrants for a single user" - {
        val grant1 =
          DeveloperPolicyGrant(
            "AdminGrant1",
            "admin-grant-1-id",
            shortTerm = false
          )
        val grant2 =
          DeveloperPolicyGrant(
            "AdminGrant2",
            "admin-grant-2-id",
            shortTerm = true
          )
        val janusData = JanusData(
          Set(account1),
          access = ACL(Map.empty, Set.empty),
          admin = ACL(
            Map("adminuser" -> ACLEntry(Set.empty, Set(grant1, grant2))),
            Set.empty
          ),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        val config = Writer.toConfig(janusData)

        "includes AdminGrant1" in {
          config should include(
            "grantName = \"\"\"AdminGrant1\"\"\", grantId = \"admin-grant-1-id\", shortTerm = false"
          )
        }

        "includes AdminGrant2" in {
          config should include(
            "grantName = \"\"\"AdminGrant2\"\"\", grantId = \"admin-grant-2-id\", shortTerm = true"
          )
        }
      }

      "includes multiple permissions and multiple DeveloperPolicyGrants for a single user" - {
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
        val grant1 =
          DeveloperPolicyGrant(
            "AdminGrant1",
            "admin-grant-1-id",
            shortTerm = false
          )
        val grant2 =
          DeveloperPolicyGrant(
            "AdminGrant2",
            "admin-grant-2-id",
            shortTerm = true
          )
        val janusData = JanusData(
          Set(account1),
          access = ACL(Map.empty, Set.empty),
          admin = ACL(
            Map(
              "adminuser" -> ACLEntry(Set(perm1, perm2), Set(grant1, grant2))
            ),
            Set.empty
          ),
          SupportACL.create(Map.empty, Set.empty),
          None
        )
        val config = Writer.toConfig(janusData)

        "includes adminPerm1" in {
          config should include("""label = "adminPerm1"""")
        }

        "includes adminPerm2" in {
          config should include("""label = "adminPerm2"""")
        }

        "includes AdminGrant1" in {
          config should include(
            "grantName = \"\"\"AdminGrant1\"\"\", grantId = \"admin-grant-1-id\", shortTerm = false"
          )
        }

        "includes AdminGrant2" in {
          config should include(
            "grantName = \"\"\"AdminGrant2\"\"\", grantId = \"admin-grant-2-id\", shortTerm = true"
          )
        }
      }
    }
  }
}
