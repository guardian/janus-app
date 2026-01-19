package logic

import com.gu.janus.model.{ACL, ACLEntry, AwsAccount}
import fixtures.Fixtures.*
import models.IamRoleInfo
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class AccountsTest
    extends AnyFreeSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with Accounts {

  val accounts = Set(fooAct, barAct, bazAct, quxAct)
  val acl = ACL(
    Map(
      "test.user" -> ACLEntry(Set(fooDev), Set.empty),
      "test.yet-another-user" -> ACLEntry(Set(fooDev), Set.empty),
      "test.admin" -> ACLEntry(Set(fooCf), Set.empty),
      "test.all" -> ACLEntry(Set(fooDev, fooCf), Set.empty),
      "test.other" -> ACLEntry(Set(fooS3), Set.empty),
      "test.zzz-other" -> ACLEntry(Set(fooS3), Set.empty),
      "test.different-account" -> ACLEntry(Set(barDev), Set.empty)
    ),
    Set.empty
  )

  "accountOwnerInformation" - {
    val emptyRoleList: (AwsAccount, Try[String]) => Set[IamRoleInfo] =
      (_, _) => Set.empty

    "uses the provided lookup function to populate the role for each account" in {
      forAll { (roleArn: String) =>
        val result = accountOwnerInformation(accounts, acl)(
          account => Success(s"${account.authConfigKey}-$roleArn"),
          emptyRoleList
        )
        result.foreach { case AccountInfo(account, _, populatedRole, _) =>
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
        accountOwnerInformation(shuffledAccounts1, acl)(
          _ => Success("role"),
          emptyRoleList
        )
      val result2 =
        accountOwnerInformation(shuffledAccounts2, acl)(
          _ => Success("role"),
          emptyRoleList
        )
      result1 shouldEqual result2
    }
  }

  "accountPermissions" - {
    "returns empty account owners if there are no owners" in {
      accountPermissions(bazAct, acl) shouldEqual Nil
    }

    "fetches all the permissions for an account, ordered by username" in {
      accountPermissions(fooAct, acl) shouldEqual List(
        UserPermissions("test.admin", Set(fooCf)),
        UserPermissions("test.all", Set(fooDev, fooCf)),
        UserPermissions("test.other", Set(fooS3)),
        UserPermissions("test.user", Set(fooDev)),
        UserPermissions("test.yet-another-user", Set(fooDev)),
        UserPermissions("test.zzz-other", Set(fooS3))
      )
    }

    "fetches all the permissions for a different account" in {
      accountPermissions(barAct, acl) shouldEqual List(
        UserPermissions("test.different-account", Set(barDev))
      )
    }
  }

  "accountIdErrors" - {
    "returns an empty list if all accounts were successfully looked up" in {
      val accountData = Set(
        AccountInfo(fooAct, List.empty, Success("foo-role"), Set.empty),
        AccountInfo(barAct, List.empty, Success("bar-role"), Set.empty),
        AccountInfo(bazAct, List.empty, Success("baz-role"), Set.empty),
        AccountInfo(quxAct, List.empty, Success("qux-role"), Set.empty)
      )
      accountIdErrors(accountData) shouldEqual Set.empty
    }

    "returns a list of accounts that failed their lookup" in {
      val accountData = Set(
        AccountInfo(fooAct, List.empty, Success("foo-role"), Set.empty),
        AccountInfo(barAct, List.empty, Success("bar-role"), Set.empty),
        AccountInfo(
          bazAct,
          List.empty,
          Failure(new RuntimeException("baz-error")),
          Set.empty
        ),
        AccountInfo(
          quxAct,
          List.empty,
          Failure(new RuntimeException("qux-error")),
          Set.empty
        )
      )
      val errorAccounts = accountIdErrors(accountData).map(_._1)
      errorAccounts shouldEqual Set(bazAct, quxAct)
    }
  }
}
