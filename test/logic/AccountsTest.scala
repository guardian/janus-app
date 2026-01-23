package logic

import com.gu.janus.model.{ACL, ACLEntry, AwsAccount}
import fixtures.Fixtures.*
import models.*
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

  private val accounts = Set(fooAct, barAct, bazAct, quxAct)
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
        val result = Accounts.accountOwnerInformation(Map.empty, accounts, acl)(
          account => Success(s"${account.authConfigKey}-$roleArn")
        )
        result.foreach { case AccountInfo(account, _, populatedRole, _, _) =>
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
        Accounts.accountOwnerInformation(Map.empty, shuffledAccounts1, acl)(_ =>
          Success("role")
        )
      val result2 =
        Accounts.accountOwnerInformation(Map.empty, shuffledAccounts2, acl)(_ =>
          Success("role")
        )
      result1 shouldEqual result2
    }
  }

  "accountPermissions" - {
    "returns empty account owners if there are no owners" in {
      Accounts.accountPermissions(bazAct, acl) shouldEqual Nil
    }

    "fetches all the permissions for an account, ordered by username" in {
      Accounts.accountPermissions(fooAct, acl) shouldEqual List(
        UserPermissions("test.admin", Set(fooCf)),
        UserPermissions("test.all", Set(fooDev, fooCf)),
        UserPermissions("test.other", Set(fooS3)),
        UserPermissions("test.user", Set(fooDev)),
        UserPermissions("test.yet-another-user", Set(fooDev)),
        UserPermissions("test.zzz-other", Set(fooS3))
      )
    }

    "fetches all the permissions for a different account" in {
      Accounts.accountPermissions(barAct, acl) shouldEqual List(
        UserPermissions("test.different-account", Set(barDev))
      )
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
                Some(s"description${a.name}"),
                a
              )
            ),
            Instant.now()
          )
        )
      )
      .toMap

  val accountsWithOnlyFailedFetches
      : Map[AwsAccount, AwsAccountIamRoleInfoStatus] =
    accounts
      .map(a =>
        a -> AwsAccountIamRoleInfoStatus.failure(
          None,
          FailureSnapshot(s"Failed to fetch ${a.name}", Instant.now())
        )
      )
      .toMap

  val accountsWithFailedFetchesAndStaleFetches
      : Map[AwsAccount, AwsAccountIamRoleInfoStatus] =
    accounts
      .map(a =>
        a -> AwsAccountIamRoleInfoStatus.failure(
          Some(
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
                  Some(s"description${a.name}"),
                  a
                )
              ),
              Instant.now()
            )
          ),
          FailureSnapshot(s"Failed to fetch ${a.name}", Instant.now())
        )
      )
      .toMap

  "lookupAccountRoles" - {
    "returns something with a good account key to look up" in {
      Accounts.lookupAccountRoles(
        accountsWithSuccessfullyFetchedTrivialRoles,
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
          Some("descriptionFoo"),
          fooAct
        )
      )
    }

    "returns nothing with a bad account key to look up" in {
      Accounts.lookupAccountRoles(
        accountsWithSuccessfullyFetchedTrivialRoles,
        fooAct,
        Failure[String](new Exception("This is rubbish"))
      ) shouldEqual Set.empty
    }
  }

  "successfulRolesForThisAccount" - {
    "when snapshot has succeeded" in {
      Accounts.successfulRolesForThisAccount(
        accountsWithSuccessfullyFetchedTrivialRoles,
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
          Some("descriptionFoo"),
          fooAct
        )
      )
    }
    "when snapshot has failed " in {
      Accounts.successfulRolesForThisAccount(
        accountsWithOnlyFailedFetches,
        fooAct.authConfigKey
      ) shouldEqual List.empty
    }

  }
  "errorRolesForThisAccount" - {
    "when snapshot has succeeded" in {
      Accounts.errorRolesForThisAccount(
        accountsWithSuccessfullyFetchedTrivialRoles,
        fooAct.authConfigKey
      ) shouldBe {
        None
      }
    }

    "when snapshot has failed" in {
      Accounts.errorRolesForThisAccount(
        accountsWithOnlyFailedFetches,
        fooAct.authConfigKey
      ) shouldBe {
        Some("Failed to fetch Foo")
      }
    }
  }

  "getAccountRolesAndStatus" - {
    "when snapshot has succeeded" in {
      val results = Accounts.getAccountRolesAndStatus(
        accountsWithSuccessfullyFetchedTrivialRoles
      )
      results.keys.toSet shouldBe accounts.map(_.name)
      results.values.flatMap(_._1.map(_.account)).toSet shouldBe accounts
      results.values.flatMap(_._2).toSet shouldBe Set.empty
    }

    "when snapshot has failed but has a stale cache entry" in {
      val results = Accounts.getAccountRolesAndStatus(
        accountsWithFailedFetchesAndStaleFetches
      )
      results.keys.toSet shouldBe accounts.map(_.name)
      results.values.flatMap(_._1.map(_.account)).toSet shouldBe accounts
      results.values.flatMap(_._2).toSet shouldBe accounts.map(a =>
        s"Failed to fetch ${a.name}"
      )
    }

    "when snapshot has failed" in {
      val results =
        Accounts.getAccountRolesAndStatus(accountsWithOnlyFailedFetches)
      results.keys.toSet shouldBe accounts.map(_.name)
      results.values.flatMap(_._1.map(_.account)).toSet shouldBe Set.empty
      results.values.flatMap(_._2).toSet shouldBe accounts.map(a =>
        s"Failed to fetch ${a.name}"
      )
    }
  }

}
