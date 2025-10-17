package com.gu.janus

import com.gu.janus.JanusConfig.JanusConfigurationException
import com.gu.janus.model.{
  ACL,
  ACLEntry,
  AwsAccount,
  JanusData,
  Permission,
  Role,
  SupportACL
}
import com.gu.janus.policy.Iam.Effect.Allow
import com.gu.janus.policy.Iam.{Action, Policy, Resource, Statement}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.Duration

class JanusConfigTest extends AnyFreeSpec with Matchers {
  val account1 = AwsAccount("Test 1", "test1")
  val account2 = AwsAccount("Test 2", "test2")
  val simpleStatement = Statement(
    Allow,
    Seq(Action("sts:GetCallerIdentity")),
    Seq(Resource("*"))
  )
  val simplePolicy = Policy(Seq(simpleStatement))

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

  "allPermissions" - {
    "returns nothing for an empty JanusData" in {
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map.empty, Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
        None
      )
      JanusConfig.allPermissions(janusData) shouldBe empty
    }

    "includes default access permissions" in {
      val permission =
        Permission(account1, "perm1", "Test permission", simplePolicy, false)
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map.empty, Set(permission)),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
        None
      )
      JanusConfig.allPermissions(janusData) shouldEqual Set(permission)
    }

    "includes access permissions" in {
      val permission =
        Permission(account1, "perm1", "Test permission", simplePolicy, false)
      val janusData = JanusData(
        Set.empty,
        access =
          ACL(Map("user1" -> ACLEntry(Set(permission), Set.empty)), Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
        None
      )
      JanusConfig.allPermissions(janusData) shouldEqual Set(permission)
    }

    "includes access permissions from multiple users" in {
      val permission1 =
        Permission(account1, "perm1", "Test permission 1", simplePolicy, false)
      val permission2 =
        Permission(account1, "perm2", "Test permission 2", simplePolicy, false)
      val janusData = JanusData(
        Set.empty,
        access = ACL(
          Map(
            "user1" -> ACLEntry(Set(permission1), Set.empty),
            "user2" -> ACLEntry(Set(permission2), Set.empty)
          ),
          Set.empty
        ),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
        None
      )
      JanusConfig.allPermissions(janusData) shouldEqual Set(
        permission1,
        permission2
      )
    }

    "includes admin permissions" in {
      val permission1 =
        Permission(account1, "perm1", "Test permission 1", simplePolicy, false)
      val permission2 =
        Permission(account1, "perm2", "Test permission 2", simplePolicy, false)
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map.empty, Set.empty),
        admin = ACL(
          Map(
            "admin1" -> ACLEntry(Set(permission1), Set.empty),
            "admin2" -> ACLEntry(Set(permission2), Set.empty)
          ),
          Set.empty
        ),
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
        None
      )
      JanusConfig.allPermissions(janusData) shouldEqual Set(
        permission1,
        permission2
      )
    }

    "includes support permissions" in {
      val permission =
        Permission(account1, "perm", "Test permission", simplePolicy, false)
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map.empty, Set.empty),
        admin = ACL(Map.empty, Set.empty),
        support = SupportACL
          .create(Map.empty, Set(permission), Duration.ofSeconds(100)),
        None
      )
      JanusConfig.allPermissions(janusData) shouldEqual Set(permission)
    }

    "includes permissions from all sources" in {
      val permission1 =
        Permission(account1, "perm1", "Test permission 1", simplePolicy, false)
      val permission2 =
        Permission(account1, "perm2", "Test permission 2", simplePolicy, false)
      val permission3 =
        Permission(account1, "perm3", "Test permission 3", simplePolicy, false)
      val permission4 =
        Permission(account1, "perm4", "Test permission 4", simplePolicy, false)
      val permission5 =
        Permission(account1, "perm5", "Test permission 5", simplePolicy, false)
      val janusData = JanusData(
        Set.empty,
        access = ACL(
          Map(
            "user1" -> ACLEntry(Set(permission1), Set.empty),
            "user2" -> ACLEntry(Set(permission2), Set.empty)
          ),
          Set.empty
        ),
        admin = ACL(
          Map(
            "admin1" -> ACLEntry(Set(permission3), Set.empty),
            "admin2" -> ACLEntry(Set(permission4), Set.empty)
          ),
          Set.empty
        ),
        SupportACL.create(Map.empty, Set(permission5), Duration.ofSeconds(100)),
        None
      )
      JanusConfig.allPermissions(janusData) shouldEqual Set(
        permission1,
        permission2,
        permission3,
        permission4,
        permission5
      )
    }

    "includes indirect user permissions that come from roles" in {
      val permission1 =
        Permission(account1, "perm1", "Test permission 1", simplePolicy, false)
      val permission2 =
        Permission(account1, "perm2", "Test permission 2", simplePolicy, false)
      val role = Role(
        "Test Role 1",
        Set(permission2)
      )
      val janusData = JanusData(
        Set.empty,
        access = ACL(
          Map(
            "user1" -> ACLEntry(Set(permission1), Set(role))
          ),
          Set.empty
        ),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
        None
      )
      JanusConfig.allPermissions(janusData) should contain(
        permission2
      )
    }
  }

  "allRoles" - {
    "returns nothing for an empty JanusData" in {
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map.empty, Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
        None
      )
      JanusConfig.allRoles(janusData) shouldBe empty
    }

    "includes roles from access ACLs" in {
      val role1 = Role("Role 1", Set.empty)
      val role2 = Role("Role 2", Set.empty)
      val janusData = JanusData(
        Set.empty,
        access = ACL(
          Map(
            "user1" -> ACLEntry(Set.empty, Set(role1)),
            "user2" -> ACLEntry(Set.empty, Set(role2))
          ),
          Set.empty
        ),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
        None
      )
      JanusConfig.allRoles(janusData) shouldEqual Set(role1, role2)
    }

    "includes roles from admin ACLs" in {
      val role1 = Role("Role 1", Set.empty)
      val role2 = Role("Role 2", Set.empty)
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map.empty, Set.empty),
        admin = ACL(
          Map(
            "admin1" -> ACLEntry(Set.empty, Set(role1)),
            "admin2" -> ACLEntry(Set.empty, Set(role2))
          ),
          Set.empty
        ),
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
        None
      )
      JanusConfig.allRoles(janusData) shouldEqual Set(role1, role2)
    }
  }

  "allAclPermissions" - {
    "includes all direct permissions" in {
      val permission1 =
        Permission(account1, "perm1", "Test permission 1", simplePolicy, false)
      val permission2 =
        Permission(account1, "perm2", "Test permission 2", simplePolicy, false)
      val acl = ACL(
        Map(
          "user1" -> ACLEntry(Set(permission1), Set.empty),
          "user2" -> ACLEntry(Set(permission2), Set.empty)
        ),
        Set.empty
      )
      JanusConfig.allAclPermissions(acl) shouldEqual Set(
        permission1,
        permission2
      )
    }

    "includes permissions from roles assigned to users" in {
      val permission1 =
        Permission(account1, "perm1", "Test permission 1", simplePolicy, false)
      val permission2 =
        Permission(account1, "perm2", "Test permission 2", simplePolicy, false)
      val role = Role(
        "Test Role 1",
        Set(permission2)
      )
      val acl = ACL(
        Map(
          "user1" -> ACLEntry(Set(permission1), Set(role))
        ),
        Set.empty
      )
      JanusConfig.allAclPermissions(acl) shouldEqual Set(
        permission1,
        permission2
      )
    }
  }
}
