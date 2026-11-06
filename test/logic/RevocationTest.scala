package logic

import com.gu.janus.model.AwsAccount
import org.scalacheck.Prop.{propBoolean, forAll}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class RevocationTest extends AnyFreeSpec with Matchers with Checkers {
  import Revocation._

  "checkConfirmation" - {
    val account = AwsAccount("Test Account", "test-account")

    "matches exact account name" in {
      checkConfirmation("Test Account", account) shouldEqual true
    }

    "matches account name (different case)" in {
      checkConfirmation("teSt aCCount", account) shouldEqual true
    }

    "matches exact account id" in {
      checkConfirmation("test-account", account) shouldEqual true
    }

    "matches account id (different case)" in {
      checkConfirmation("TEST-account", account) shouldEqual true
    }

    "does not match wrong key" in {
      checkConfirmation("invalid", account) shouldEqual false
    }

    "does not match any incorrect key" in {
      check(forAll { (s: String) =>
        (s.toLowerCase != account.authConfigKey && s.toLowerCase != account.name) ==>
          (checkConfirmation(s, account) == false)
      })
    }
  }
}
