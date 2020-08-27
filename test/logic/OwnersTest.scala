package logic

import com.gu.janus.model.{ACL, AccountOwners}
import fixtures.Fixtures._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers


class OwnersTest extends AnyFreeSpec with Matchers {
  import Owners._

  val acl = ACL(Map(
    "test.user" -> Set(fooDev),
    "test.yet-another-user" -> Set(fooDev),
    "test.admin" -> Set(fooCf),
    "test.all" -> Set(fooDev, fooCf),
    "test.other" -> Set(fooS3),
    "test.zzz-other" -> Set(fooS3),
    "test.different-account" -> Set(barDev)
  ), Set.empty)

  "accountOwners" - {
    "returns empty account owners if there are no owners" in {
      accountOwners(bazAct, acl) shouldEqual AccountOwners.empty
    }

    "fetches all the admins for an account" in {
      accountOwners(fooAct, acl).admins.toSet shouldEqual Set("test.admin", "test.all")
    }

    "orders admins by username" in {
      accountOwners(fooAct, acl).admins shouldEqual List("test.admin", "test.all")
    }

    "fetches developers and excludes those that are also admins" in {
      accountOwners(fooAct, acl).devs.toSet shouldEqual Set("test.user", "test.yet-another-user")
    }

    "orders developers by username" in {
      accountOwners(fooAct, acl).devs shouldEqual List("test.user", "test.yet-another-user")
    }

    "fetches 'other users' and excludes those that are also admins and devs" in {
      accountOwners(fooAct, acl).others.toSet shouldEqual Set("test.other", "test.zzz-other")
    }

    "orders 'other users' by username" in {
      accountOwners(fooAct, acl).others shouldEqual List("test.other", "test.zzz-other")
    }
  }
}
