package com.gu.janus.policy

import com.gu.janus.policy.Statements._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class StatementsTest extends AnyFreeSpec with Matchers {
  "policy helper" - {
    val statements = Seq(
      s3FullAccess("my-bucket", "/"),
      s3ReadAccess("your-bucket", "/"),
      s3ReadAccess("your-bucket", "/")
    )

    "deduplicates statements" in {
      val p = policy(statements: _*)
      p.statements.distinct shouldBe p.statements
    }
  }

  "enforceCorrectPath" - {
    "returns true for /" in {
      enforceCorrectPath("/") shouldEqual true
    }

    "returns true for path with leading slash and no trailing slash" in {
      enforceCorrectPath("/correct/path") shouldEqual true
    }

    "returns false if no leading slash" in {
      enforceCorrectPath("invalid/path") shouldEqual false
    }

    "returns false with trailing and leading slash" in {
      enforceCorrectPath("/bad/trailing/slash/") shouldEqual false
    }

    "returns false for edge case of single char" in {
      enforceCorrectPath("f") shouldEqual false
    }
  }

  "hierarchyPath" - {
    "builds a '/*' pattern for the path '/'" in {
      hierarchyPath("/") shouldEqual "/*"
    }

    "builds a '/my-path/sub-path/*' pattern for the path '/my-path/sub-path'" in {
      hierarchyPath("/my-path/sub-path") shouldEqual "/my-path/sub-path/*"
    }
  }
}
