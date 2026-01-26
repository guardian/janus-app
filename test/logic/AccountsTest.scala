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
    "should return full account information when given an existing account key " in {
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

    "should return an empty set when given an non-existing account key" in {
      Accounts
        .lookupAccountRoles(
          accountsWithSuccessfullyFetchedTrivialRoles,
          fooAct,
          Failure[String](new Exception("Unable to look up this account"))
        )
        .isEmpty shouldBe true
    }
  }

  "successfulRolesForThisAccount" - {
    "should return full account info when the snapshot has succeeded for this account" in {
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
    "should return nothing when the snapshot has failed for this account" in {
      Accounts
        .successfulRolesForThisAccount(
          accountsWithOnlyFailedFetches,
          fooAct.authConfigKey
        )
        .isEmpty shouldBe true
    }
  }

  "errorRolesForThisAccount" - {
    "should return no errors when the snapshot has succeeded" in {
      Accounts
        .errorRolesForThisAccount(
          accountsWithSuccessfullyFetchedTrivialRoles,
          fooAct.authConfigKey
        )
        .isEmpty shouldBe true
    }

    "should return the failure when the snapshot has failed" in {
      Accounts.errorRolesForThisAccount(
        accountsWithOnlyFailedFetches,
        fooAct.authConfigKey
      ) shouldBe Some("Failed to fetch Foo")
    }
  }

  "getAccountRolesAndStatus" - {
    val accountNames = accounts.map(_.name)

    "should return a key for every account when snapshot has succeeded" in {
      val keys = Accounts
        .getAccountRolesAndStatus(
          accountsWithSuccessfullyFetchedTrivialRoles
        )
        .keys
        .toSet
      keys shouldBe accountNames
    }

    "should return a value for every account when snapshot has succeeded (checks cross reference in map)" in {
      val returnedAccounts = Accounts
        .getAccountRolesAndStatus(
          accountsWithSuccessfullyFetchedTrivialRoles
        )
        .values
        .flatMap(_._1.map(_.account))
        .toSet
      returnedAccounts shouldBe accounts
    }

    "should return no errors when snapshot has succeeded" in {
      val errors = Accounts
        .getAccountRolesAndStatus(
          accountsWithSuccessfullyFetchedTrivialRoles
        )
        .values
        .flatMap(_._2)
      errors.isEmpty shouldBe true
    }

    "should return a key for every account when snapshot has failed but each account has a stale cache entry" in {
      val keys = Accounts
        .getAccountRolesAndStatus(
          accountsWithFailedFetchesAndStaleFetches
        )
        .keys
        .toSet
      keys shouldBe accountNames
    }

    "should return a value for every account when snapshot has failed but each account has a stale cache entry (checks cross reference in map)" in {
      val returnedAccounts = Accounts
        .getAccountRolesAndStatus(
          accountsWithFailedFetchesAndStaleFetches
        )
        .values
        .flatMap(_._1.map(_.account))
        .toSet
      returnedAccounts shouldBe accounts
    }

    "should return an error for every account when snapshot has failed but each account has a stale cache entry" in {
      val errors = Accounts
        .getAccountRolesAndStatus(
          accountsWithFailedFetchesAndStaleFetches
        )
        .values
        .flatMap(_._2)
        .toSet
      errors shouldBe accounts.map(a => s"Failed to fetch ${a.name}")
    }

    "should return a key for every account when snapshot has failed without even a stale cache entry" in {
      val keys = Accounts
        .getAccountRolesAndStatus(
          accountsWithOnlyFailedFetches
        )
        .keys
        .toSet
      keys shouldBe accountNames
    }

    "should return no value for any account when snapshot has failed without even a stale cache entry" in {
      val returnedAccounts =
        Accounts
          .getAccountRolesAndStatus(
            accountsWithOnlyFailedFetches
          )
          .values
          .flatMap(_._1.map(_.account))
          .toSet
      returnedAccounts shouldBe Set.empty
    }

    "should return an error for every account when snapshot has failed without even a stale cache entry" in {
      val errors =
        Accounts
          .getAccountRolesAndStatus(
            accountsWithOnlyFailedFetches
          )
          .values
          .flatMap(_._2)
          .toSet
      errors shouldBe accounts.map(a => s"Failed to fetch ${a.name}")
    }
  }

}
