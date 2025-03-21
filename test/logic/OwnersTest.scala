package logic

import com.gu.janus.model.ACL
import fixtures.Fixtures._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class OwnersTest extends AnyFreeSpec with Matchers {
  import Owners._

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
}
