package com.gu.janus.config

import com.gu.janus.model._
import com.gu.janus.policy.Iam.{Action, Policy, Resource, Statement}
import com.gu.janus.policy.Iam.Effect.Allow
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.Duration

class WriterTest extends AnyFreeSpec with Matchers {
  val account1 = AwsAccount("Test 1", "test1")
  val account2 = AwsAccount("Test 2", "test2")
  val simpleStatement = Statement(
    Allow,
    Seq(Action("sts:GetCallerIdentity")),
    Seq(Resource("*"))
  )
  val simplePolicy = Policy(Seq(simpleStatement))

  "allPermissions" - {
    "returns nothing for an empty JanusData" in {
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map.empty, Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
        None
      )
      Writer.allPermissions(janusData) shouldBe empty
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
      Writer.allPermissions(janusData) shouldEqual Set(permission)
    }

    "includes access permissions" in {
      val permission =
        Permission(account1, "perm1", "Test permission", simplePolicy, false)
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map("user1" -> Set(permission)), Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
        None
      )
      Writer.allPermissions(janusData) shouldEqual Set(permission)
    }

    "includes access permissions from mulitple users" in {
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
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
        None
      )
      Writer.allPermissions(janusData) shouldEqual Set(permission1, permission2)
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
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
        None
      )
      Writer.allPermissions(janusData) shouldEqual Set(permission1, permission2)
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
      Writer.allPermissions(janusData) shouldEqual Set(permission)
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
        SupportACL.create(Map.empty, Set(permission5), Duration.ofSeconds(100)),
        None
      )
      Writer.allPermissions(janusData) shouldEqual Set(
        permission1,
        permission2,
        permission3,
        permission4,
        permission5
      )
    }
  }

  "toConfig" - {
    "includes the support period" in {
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map.empty, Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(123456789)),
        None
      )
      Writer.toConfig(janusData) should include("period = 123456789")
    }

    "includes the support period even if it was specified using a non-second period" in {
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map.empty, Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty, Duration.ofDays(7)),
        None
      )
      Writer.toConfig(janusData) should include("period = 604800")
    }

    "includes the permissionsRepo" in {
      val janusData = JanusData(
        Set.empty,
        access = ACL(Map.empty, Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty, Duration.ofDays(7)),
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
        SupportACL.create(Map.empty, Set.empty, Duration.ofDays(7)),
        None
      )
      Writer.toConfig(janusData) should not include "permissionsRepo"
    }
  }
}
