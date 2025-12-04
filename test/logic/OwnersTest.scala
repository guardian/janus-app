package logic

import com.gu.janus.model.ACL
import fixtures.Fixtures._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.language.postfixOps
import scala.util.{Failure, Success}

class OwnersTest
    extends AnyFreeSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks {
  import Owners._

  val accounts = List(fooAct, barAct, bazAct, quxAct)
  val acl = ACL(
    Map(
      "test.user" -> Set(fooDev),
      "test.yet-another-user" -> Set(fooDev),
      "test.admin" -> Set(fooCf),
      "test.all" -> Set(fooDev, fooCf),
      "test.other" -> Set(fooS3),
      "test.zzz-other" -> Set(fooS3),
      "test.different-account" -> Set(barDev)
    ),
    Set.empty
  )

  "accountOwnerInformation" - {
    "uses the provided lookup function to populate the role for each account" in {
      forAll { (roleArn: String) =>
        val result = accountOwnerInformation(accounts, acl)(account =>
          Success(s"${account.authConfigKey}-$roleArn")
        )
        result.foreach { case (account, _, populatedRole) =>
          populatedRole shouldEqual Success(
            s"${account.authConfigKey}-$roleArn"
          )
        }
      }
    }

    "sorts AWS accounts" in {
      val shuffledAccounts1 = scala.util.Random.shuffle(accounts)
      val shuffledAccounts2 = scala.util.Random.shuffle(accounts)
      val result1 =
        accountOwnerInformation(shuffledAccounts1, acl)(_ => Success("role"))
      val result2 =
        accountOwnerInformation(shuffledAccounts2, acl)(_ => Success("role"))
      result1 shouldEqual result2
    }
  }

  "accountPermissions" - {
    "returns empty account owners if there are no owners" in {
      accountPermissions(bazAct, acl) shouldEqual Nil
    }

    "fetches all the permissions for an account, ordered by username" in {
      accountPermissions(fooAct, acl) shouldEqual List(
        "test.admin" -> Set(fooCf),
        "test.all" -> Set(fooDev, fooCf),
        "test.other" -> Set(fooS3),
        "test.user" -> Set(fooDev),
        "test.yet-another-user" -> Set(fooDev),
        "test.zzz-other" -> Set(fooS3)
      )
    }

    "fetches all the permissions for a different account" in {
      accountPermissions(barAct, acl) shouldEqual List(
        "test.different-account" -> Set(barDev)
      )
    }
  }

  "accountIdErrors" - {
    "returns an empty list if all accounts were successfully looked up" in {
      val accountData = List(
        (fooAct, List.empty, Success("foo-role")),
        (barAct, List.empty, Success("bar-role")),
        (bazAct, List.empty, Success("baz-role")),
        (quxAct, List.empty, Success("qux-role"))
      )
      accountIdErrors(accountData) shouldEqual Nil
    }

    "returns a list of accounts that failed their lookup" in {
      val accountData = List(
        (fooAct, List.empty, Success("foo-role")),
        (barAct, List.empty, Success("bar-role")),
        (bazAct, List.empty, Failure(new RuntimeException("baz-error"))),
        (quxAct, List.empty, Failure(new RuntimeException("qux-error")))
      )
      val errorAccounts = accountIdErrors(accountData).map(_._1)
      errorAccounts shouldEqual List(bazAct, quxAct)
    }
  }
}
