package com.gu.janus.policy

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class PolicyJsonTest extends AnyFreeSpec with Matchers {
  "stripSids" - {
    "removes the Sid field from a simple policy document" in {
      val json = """{"Version":"2012-10-17","Statement":[{"Sid":"1","Effect":"Allow","Action":["*"],"Resource":["*"]}]}"""
      PolicyJson.stripSids(json) shouldEqual """{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["*"],"Resource":["*"]}]}"""
    }

    "removes Sids from multiple policy statements" in {
      val json =
        """{"Version":"2012-10-17","Statement":[{"Sid":"1","Effect":"Allow","Action":["guardduty:List*"],"Resource":["*"]},{"Sid":"2","Effect":"Allow","Action":["s3:*"],"Resource":["arn:aws:s3:::example-bucket"]}]}"""
      PolicyJson.stripSids(json) shouldEqual """{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["guardduty:List*"],"Resource":["*"]},{"Effect":"Allow","Action":["s3:*"],"Resource":["arn:aws:s3:::example-bucket"]}]}"""
    }

    "does nothing if a policy document has no Sids" in {
      val json = """{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["*"],"Resource":["*"]}]}"""
      PolicyJson.stripSids(json) shouldEqual json
    }

    "does nothing if the JSON is unrelated" in {
      val json = """{"foo":"bar"}"""
      PolicyJson.stripSids(json) shouldEqual json
    }
  }
}
