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
        access = ACL(Map("user1" -> Set(permission)), Set.empty),
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
        access = ACL(Map("user1" -> Set(permission)), Set.empty),
        admin = ACL(Map.empty, Set.empty),
        SupportACL.create(Map.empty, Set.empty),
        None
      )
      Writer.toConfig(janusData) should include(
        """arn:aws:iam::aws:policy/ReadOnlyAccess"""
      )
    }
  }
}
