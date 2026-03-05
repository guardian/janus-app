package logic

import com.gu.janus.model.{AwsAccount, Permission}
import fixtures.Fixtures.*
import logic.AccountOrdering.given
import models.{AccountAccess, DeveloperPolicy}
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.util.Random.shuffle

class AccountOrderingTest
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaCheckDrivenPropertyChecks {
  import AccountOrdering.*

  /** Converts a flat set of permissions and developer policies into a
    * Map[AwsAccount, AccountAccess] for use with [[orderedAccountAccess]].
    */
  private def toAccountAccessMap(
      perms: Set[Permission],
      developerPolicies: Set[DeveloperPolicy]
  ): Map[AwsAccount, AccountAccess] = {
    val allAccounts = perms.map(_.account) ++ developerPolicies.map(_.account)
    allAccounts.map { account =>
      account -> AccountAccess(
        permissions = perms.filter(_.account == account).toList,
        developerPolicies =
          developerPolicies.filter(_.account == account).toList
      )
    }.toMap
  }

  "userAccountAccess" - {
    val perms = Set(bazDev, fooS3, fooDev, fooCf, barDev, quxDev, quxCf)
    val devPolicies = Set(
      developerPolicyAlphaFoo1,
      developerPolicyAlphaFoo2,
      developerPolicyAlphaBar,
      developerPolicyBetaFoo,
      developerPolicyBetaBar,
      developerPolicyGammaBaz,
      developerPolicyDeltaQux
    )
    val devPolicyGrants = Set(grantAlpha, grantBeta, grantGamma)
    val awsAccountsAccess = toAccountAccessMap(perms, devPolicies)

    "orders AWS accounts" - {
      "given no favourites" - {
        "sorts accounts by the number of available permissions, descending" in {
          val result =
            orderedAccountAccess(awsAccountsAccess, devPolicyGrants)
              .map(_.awsAccount)
          result shouldEqual List(fooAct, quxAct, barAct, bazAct)
        }
      }

      "given favourite accounts" - {
        "puts a favourite first" in {
          val result = orderedAccountAccess(
            awsAccountsAccess,
            devPolicyGrants,
            List("baz")
          )
            .map(_.awsAccount)
            .head
          result shouldEqual bazAct
        }

        "preserves sorting of non-favourite accounts" in {
          val result = orderedAccountAccess(
            awsAccountsAccess,
            devPolicyGrants,
            List("baz")
          )
            .map(_.awsAccount)
            .tail
          result shouldEqual List(fooAct, quxAct, barAct)
        }

        "sorts favourites by provided order" in {
          val result =
            orderedAccountAccess(
              awsAccountsAccess,
              devPolicyGrants,
              List("baz", "bar")
            )
              .map(_.awsAccount)
          result shouldEqual List(bazAct, barAct, fooAct, quxAct)
        }
      }
    }

    "sorts the account permissions" in {
      val fooPerms =
        orderedAccountAccess(awsAccountsAccess, devPolicyGrants, Nil)
          .find(_.awsAccount == fooAct)
          .value
          .permissions
      fooPerms shouldEqual List(
        developerPermission(fooAct),
        s3ManagerPermission(fooAct),
        accountAdminPermission(fooAct)
      )
    }

    "handles developer policy access" - {
      "groups policies by grant, within an account" in {
        val fooPolicies =
          orderedAccountAccess(awsAccountsAccess, devPolicyGrants)
            .find(_.awsAccount == fooAct)
            .value
            .developerPolicies
        // toSet because we aren't testing the order of groups here
        fooPolicies.toSet shouldEqual Set(
          grantAlpha -> List(
            developerPolicyAlphaFoo1,
            developerPolicyAlphaFoo2
          ),
          grantBeta -> List(developerPolicyBetaFoo)
        )
      }

      "should order the grant groups alphabetically by name" in {
        val result =
          orderedAccountAccess(awsAccountsAccess, devPolicyGrants)
            .find(_.awsAccount == fooAct)
            .value
            .developerPolicies
            .map(_._1)
        result shouldEqual List(grantAlpha, grantBeta)
      }

      "orders developer policies alphabetically by name within each group" in {
        val genAlphaFooDevPolicy: Gen[DeveloperPolicy] =
          for {
            policyNum <- Gen.choose(1, 100)
            nameLength <- Gen.choose(5, 40)
            name <- Gen.listOfN(nameLength, Gen.alphaChar).map(_.mkString(""))
          } yield {
            DeveloperPolicy(
              policyArnString =
                s"arn:aws:iam::123456789012:policy/alpha-$policyNum",
              policyName = name,
              policyGrantId = grantAlpha.id,
              description = Some(s"Alpha policy $policyNum: $name"),
              account = fooAct
            )
          }

        forAll(Gen.listOfN(10, genAlphaFooDevPolicy)) { devPolicies =>
          whenever(
            // make sure the generated policies have unique names
            devPolicies.map(_.policyName).distinct.size == devPolicies.size &&
              // we need at least two policies to test the ordering
              devPolicies.length >= 2
          ) {
            val awsAccountsAccess =
              toAccountAccessMap(Set.empty, devPolicies.toSet)
            val result =
              orderedAccountAccess(awsAccountsAccess, Set(grantAlpha))
                .find(_.awsAccount == fooAct)
                .value
                .developerPolicies
            result shouldEqual List(
              grantAlpha -> devPolicies.sortBy(_.policyName)
            )
          }
        }
      }

      "does not include developer policies for which the user does not have a grant" in {
        val fooPolicies =
          orderedAccountAccess(awsAccountsAccess, Set(grantAlpha))
            .find(
              // no grants for this account's developer policy
              _.awsAccount == quxAct
            )
            .value
            .developerPolicies
        fooPolicies shouldEqual Nil
      }
    }
  }

  "Permission's ordering" - {
    "preserve dev before admin" in {
      val perms =
        List(developerPermission(fooAct), accountAdminPermission(fooAct))
      perms.sorted shouldEqual perms
    }

    "put dev before admin" in {
      val perms =
        List(developerPermission(fooAct), accountAdminPermission(fooAct))
      perms.reverse.sorted shouldEqual perms
    }

    "preserve dev before another permission" in {
      val perms = List(developerPermission(fooAct), s3ReaderPermission(fooAct))
      perms.sorted shouldEqual perms
    }

    "put dev before another permission" in {
      val perms = List(developerPermission(fooAct), s3ReaderPermission(fooAct))
      perms.reverse.sorted shouldEqual perms
    }

    "preserve admin after another permission" in {
      val perms =
        List(s3ReaderPermission(fooAct), accountAdminPermission(fooAct))
      perms.sorted shouldEqual perms
    }

    "put admin after another permission" in {
      val perms =
        List(s3ReaderPermission(fooAct), accountAdminPermission(fooAct))
      perms.reverse.sorted shouldEqual perms
    }

    "preserves dev - other - admin" in {
      val perms = List(
        developerPermission(fooAct),
        s3ReaderPermission(fooAct),
        accountAdminPermission(fooAct)
      )
      perms.sorted shouldEqual perms
    }

    "puts dev - other - admin" in {
      val perms = List(
        developerPermission(fooAct),
        s3ReaderPermission(fooAct),
        accountAdminPermission(fooAct)
      )
      perms.reverse.sorted shouldEqual perms
    }

    "orders alphabetically for non dev/admin permissions" in {
      val perms = List(
        kinesisReadPermission(fooAct),
        lambdaPermission(fooAct),
        s3ManagerPermission(fooAct),
        s3ReaderPermission(fooAct)
      )
      perms.reverse.sorted shouldEqual perms
    }

    "always returns the correct order" - {
      val permissions = List(
        developerPermission(fooAct),
        kinesisReadPermission(fooAct),
        lambdaPermission(fooAct),
        s3ReaderPermission(fooAct),
        accountAdminPermission(fooAct)
      )

      "for shuffled permissions" in {
        1 to 20 foreach { _ =>
          shuffle(permissions).sorted shouldEqual permissions
        }
      }
    }
  }

  "developer policy ordering" - {
    "orders developer policies by AWS account name" in {
      val policies = List(
        developerPolicyAlphaBar,
        developerPolicyGammaBaz,
        developerPolicyAlphaFoo1,
        developerPolicyDeltaQux
      )
      1 to 20 foreach { _ =>
        shuffle(policies).sorted shouldEqual policies
      }
    }

    "orders alphabetically by policy name within an AWS account" in {
      val policies = List(
        developerPolicyAlphaFoo1,
        developerPolicyAlphaFoo2,
        developerPolicyBetaFoo
      )
      1 to 20 foreach { _ =>
        shuffle(policies).sorted shouldEqual policies
      }
    }
  }
}
