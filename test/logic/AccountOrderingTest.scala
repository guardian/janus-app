package logic

import com.gu.janus.model.{AwsAccount, Permission}
import fixtures.Fixtures.*
import logic.AccountOrdering.given
import models.AccountAccess
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random.shuffle

class AccountOrderingTest extends AnyFreeSpec with Matchers with OptionValues {
  import AccountOrdering.*

  /** Converts a flat set of permissions into a Map[AwsAccount, AccountAccess]
    * for use with [[orderedAccountAccess]], with no developer policies.
    */
  private def toAccountAccessMap(
      perms: Set[Permission]
  ): Map[AwsAccount, AccountAccess] =
    perms
      .groupBy(_.account)
      .view
      .mapValues(ps => AccountAccess(ps.toList, Nil))
      .toMap

  "userAccountAccess" - {
    val perms = Set(bazDev, fooS3, fooDev, fooCf, barDev, quxDev, quxCf)

    "given no favourites" - {
      "sorts accounts by the number of available permissions, descending" in {
        orderedAccountAccess(toAccountAccessMap(perms), Nil)
          .map(_.awsAccount) shouldEqual List(fooAct, quxAct, barAct, bazAct)
      }
    }

    "given favourite accounts" - {
      "puts a favourite first" in {
        orderedAccountAccess(toAccountAccessMap(perms), List("baz"))
          .map(_.awsAccount)
          .head shouldEqual bazAct
      }

      "preserves sorting of non-favourite accounts" in {
        orderedAccountAccess(toAccountAccessMap(perms), List("baz"))
          .map(_.awsAccount)
          .tail shouldEqual List(fooAct, quxAct, barAct)
      }

      "sorts favourites by provided order" in {
        orderedAccountAccess(toAccountAccessMap(perms), List("baz", "bar")).map(
          _.awsAccount
        ) shouldEqual List(bazAct, barAct, fooAct, quxAct)
      }
    }

    "sorts the account permissions" in {
      val fooPerms = orderedAccountAccess(toAccountAccessMap(perms), Nil)
        .find(_.awsAccount == fooAct)
        .value
        .access
        .permissions
      fooPerms shouldEqual List(
        developerPermission(fooAct),
        s3ManagerPermission(fooAct),
        accountAdminPermission(fooAct)
      )
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
}
