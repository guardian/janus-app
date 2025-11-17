package com.gu.janus.model

import com.gu.janus.policy.Iam.Effect.Allow
import com.gu.janus.policy.Iam.{Action, Policy, Resource, Statement}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class PermissionTest extends AnyFreeSpec with Matchers {
  private val account1 = AwsAccount("Test 1", "test1")
  private val simpleStatement = Statement(
    Allow,
    Seq(Action("sts:GetCallerIdentity")),
    Seq(Resource("*"))
  )
  private val simplePolicy = Policy(Seq(simpleStatement))

  "allPermissions" - {
    "returns nothing for an empty JanusData" in {
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map.empty, Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty),
        None
      )
      Permission.allPermissions(janusData) shouldBe empty
    }

    "includes default access permissions" in {
      val permission =
        Permission(account1, "perm1", "Test permission", simplePolicy, false)
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map.empty, Set(permission)),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty),
        None
      )
      Permission.allPermissions(janusData) shouldEqual Set(permission)
    }

    "includes access permissions" in {
      val permission =
        Permission(account1, "perm1", "Test permission", simplePolicy, false)
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map("user1" -> Set(permission)), Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty),
        None
      )
      Permission.allPermissions(janusData) shouldEqual Set(permission)
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
            "user1" -> Set(permission1),
            "user2" -> Set(permission2)
          ),
          Set.empty
        ),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty),
        None
      )
      Permission.allPermissions(janusData) shouldEqual Set(
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
            "admin1" -> Set(permission1),
            "admin2" -> Set(permission2)
          ),
          Set.empty
        ),
        SupportACL.create(Map.empty, Set.empty),
        None
      )
      Permission.allPermissions(janusData) shouldEqual Set(
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
          .create(Map.empty, Set(permission)),
        None
      )
      Permission.allPermissions(janusData) shouldEqual Set(permission)
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
            "user1" -> Set(permission1),
            "user2" -> Set(permission2)
          ),
          Set.empty
        ),
        admin = ACL(
          Map(
            "admin1" -> Set(permission3),
            "admin2" -> Set(permission4)
          ),
          Set.empty
        ),
        SupportACL.create(Map.empty, Set(permission5)),
        None
      )
      Permission.allPermissions(janusData) shouldEqual Set(
        permission1,
        permission2,
        permission3,
        permission4,
        permission5
      )
    }
  }
}
