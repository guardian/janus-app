package instances

import cats.syntax.semigroup._
import cats.instances.set._
import cats.instances.map._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers


class ACLMergeTest extends AnyFreeSpec with Matchers {
  "Monoid instance for ACL entries" - {
    "should correctly combine overlapping entries" in {
      val a = Map(
        "foo" -> Set(1, 2, 3),
        "bar" -> Set(4, 5, 6)
      )
      val b = Map(
        "foo" -> Set(1, 2, 10),
        "baz" -> Set(7, 8, 9)
      )
      a.combine(b) shouldEqual Map(
        "foo" -> Set(1, 2, 3, 10),
        "bar" -> Set(4, 5, 6),
        "baz" -> Set(7, 8, 9)
      )
    }

    "correctly combines non-overlapping entries" in {
      val a = Map(
        "foo" -> Set(1, 2, 3),
        "bar" -> Set(4, 5, 6)
      )
      val b = Map(
        "baz" -> Set(7, 8, 9),
        "zuk" -> Set(10, 11)
      )
      a.combine(b) shouldEqual Map(
        "foo" -> Set(1, 2, 3),
        "bar" -> Set(4, 5, 6),
        "baz" -> Set(7, 8, 9),
        "zuk" -> Set(10, 11)
      )
    }
  }
}
