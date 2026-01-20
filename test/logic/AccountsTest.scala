package logic

import com.gu.janus.model.{ACL, ACLEntry, AwsAccount}
import fixtures.Fixtures.*
import models.{
  AwsAccountIamRoleInfoStatus,
  FailureSnapshot,
  IamRoleInfo,
  IamRoleInfoSnapshot,
  AccountInfo,
  UserPermissions
}
import org.scalatest.FailedStatus
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import software.amazon.awssdk.arns.Arn

import java.time.Instant
import scala.language.{dynamics, postfixOps}
import scala.util.{Failure, Success, Try}

class AccountsTest
    extends AnyFreeSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks {

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

    "uses the provided lookup function to populate the role for each account" in new Context() {
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

    "sorts AWS accounts" in new Context {
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
    "returns empty account owners if there are no owners" in new Context() {
      accountPermissions(bazAct, acl) shouldEqual Nil
    }

    "fetches all the permissions for an account, ordered by username" in new Context {
      accountPermissions(fooAct, acl) shouldEqual List(
        UserPermissions("test.admin", Set(fooCf)),
        UserPermissions("test.all", Set(fooDev, fooCf)),
        UserPermissions("test.other", Set(fooS3)),
        UserPermissions("test.user", Set(fooDev)),
        UserPermissions("test.yet-another-user", Set(fooDev)),
        UserPermissions("test.zzz-other", Set(fooS3))
      )
    }

    "fetches all the permissions for a different account" in new Context {
      accountPermissions(barAct, acl) shouldEqual List(
        UserPermissions("test.different-account", Set(barDev))
      )
    }
  }

  "accountIdErrors" - {
    "returns an empty list if all accounts were successfully looked up" in new Context {
      val accountData = Set(
        AccountInfo(fooAct, List.empty, Success("foo-role"), Set.empty),
        AccountInfo(barAct, List.empty, Success("bar-role"), Set.empty),
        AccountInfo(bazAct, List.empty, Success("baz-role"), Set.empty),
        AccountInfo(quxAct, List.empty, Success("qux-role"), Set.empty)
      )
      accountIdErrors(accountData) shouldEqual Set.empty
    }

    "returns a list of accounts that failed their lookup" in new Context {
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

  val accountsWithSuccessfullyFetchedTrivialRoles
      : Map[AwsAccount, AwsAccountIamRoleInfoStatus] =
    accounts
      .map(a =>
        a -> AwsAccountIamRoleInfoStatus.success(
          IamRoleInfoSnapshot(
            List(
              IamRoleInfo(
                Arn
                  .builder()
                  .accountId(s"awsAccount-${a.authConfigKey}")
                  .partition("awsPartition")
                  .service("awsService")
                  .resource("awsResource")
                  .build(),
                s"provisionedRoleTagValue${a.name}",
                Some(s"friendlyName${a.name}"),
                Some(s"description${a.name}")
              )
            ),
            Instant.now()
          )
        )
      )
      .toMap

  val accountsWithFailedFetches: Map[AwsAccount, AwsAccountIamRoleInfoStatus] =
    accounts
      .map(a =>
        a -> AwsAccountIamRoleInfoStatus.failure(
          None,
          FailureSnapshot(s"Failed to fetch ${a.name}", Instant.now())
        )
      )
      .toMap

  "lookupAccountRoles" - {
    "returns something with a good account key to look up" in new Context(
      accountsWithSuccessfullyFetchedTrivialRoles
    ) {
      this.lookupAccountRoles(
        fooAct,
        Success[String](fooAct.authConfigKey)
      ) shouldEqual Set(
        IamRoleInfo(
          Arn
            .builder()
            .accountId("awsAccount-foo")
            .partition("awsPartition")
            .service("awsService")
            .resource("awsResource")
            .build(),
          "provisionedRoleTagValueFoo",
          Some("friendlyNameFoo"),
          Some("descriptionFoo")
        )
      )
    }

    "returns nothing with a bad account key to look up" in new Context(
      accountsWithSuccessfullyFetchedTrivialRoles
    ) {
      this.lookupAccountRoles(
        fooAct,
        Failure[String](new Exception("This is rubbish"))
      ) shouldEqual Set.empty
    }
  }

  "getAccountRoles" - {
    "when snapshot has succeeded " in new Context(
      accountsWithSuccessfullyFetchedTrivialRoles
    ) {
      this.getAccountRoles shouldEqual Map(
        (Some("friendlyNameBar"), "provisionedRoleTagValueBar") -> List("Bar"),
        (Some("friendlyNameBaz"), "provisionedRoleTagValueBaz") -> List("Baz"),
        (Some("friendlyNameFoo"), "provisionedRoleTagValueFoo") -> List("Foo"),
        (Some("friendlyNameQux"), "provisionedRoleTagValueQux") -> List("Qux")
      )
    }
    "when snapshot has failed " in new Context(
      accountsWithFailedFetches
    ) {
      this.getAccountRoles shouldEqual Map.empty
    }
  }

  "getFailedAccountRoles" - {
    "when snapshot has succeeded " in new Context(
      accountsWithSuccessfullyFetchedTrivialRoles
    ) {
      this.getFailedAccountRoles shouldEqual Map.empty
    }
    "when snapshot has failed " in new Context(
      accountsWithFailedFetches
    ) {
      this.getFailedAccountRoles shouldEqual
        Map(
          "Bar" -> List(Some("Failed to fetch Bar")),
          "Qux" -> List(Some("Failed to fetch Qux")),
          "Foo" -> List(Some("Failed to fetch Foo")),
          "Baz" -> List(Some("Failed to fetch Baz"))
        )
    }
  }

  "successfulRolesForThisAccount" - {
    "when snapshot has succeeded " in new Context(
      accountsWithSuccessfullyFetchedTrivialRoles
    ) {
      this.successfulRolesForThisAccount(
        fooAct.authConfigKey
      ) shouldEqual List(
        IamRoleInfo(
          Arn
            .builder()
            .accountId("awsAccount-foo")
            .resource("awsResource")
            .partition("awsPartition")
            .service("awsService")
            .build(),
          "provisionedRoleTagValueFoo",
          Some("friendlyNameFoo"),
          Some("descriptionFoo")
        )
      )
    }
    "when snapshot has failed " in new Context(
      accountsWithFailedFetches
    ) {
      this.successfulRolesForThisAccount(
        fooAct.authConfigKey
      ) shouldEqual List.empty
    }

  }
  "errorRolesForThisAccount" - {}

  class Context(
      val rolesStatuses: Map[AwsAccount, AwsAccountIamRoleInfoStatus] =
        Map.empty
  ) extends Accounts {}
}
