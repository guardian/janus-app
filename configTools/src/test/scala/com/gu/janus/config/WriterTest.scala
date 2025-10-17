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
        access = ACL(Map("user1" -> ACLEntry(Set(permission), Set.empty)), Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
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
        access = ACL(Map("user1" -> ACLEntry(Set(permission), Set.empty)), Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty, Duration.ofSeconds(100)),
        None
      )
      Writer.toConfig(janusData) should include(
        """arn:aws:iam::aws:policy/ReadOnlyAccess"""
      )
    }
  }
}
