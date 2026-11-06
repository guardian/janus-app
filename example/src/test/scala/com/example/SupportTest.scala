package com.example

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class SupportTest extends AnyFreeSpec with Matchers {
  "Future support rotas should not contain users that are not on the user access list" in {
    val now = Instant.now
    val supportUsers = Support.acl.rota
      .filter { case (dateTime, _) => dateTime.isAfter(now) }
      .flatMap { case (_, (user1, user2)) => Seq(user1, user2) }
      .toSet
      .filterNot(_ == Support.tbd)

    val accessUsers = Access.acl.userAccess.map { case (user, _) => user }.toSet

    val missingUsers = supportUsers -- accessUsers
    missingUsers shouldBe empty
  }

  "Support dates should not be duplicated" in {
    val duplicateDates: Map[(Int, Int, Int), Int] = Support.rota
      .groupBy { case (date, _) => date }
      .filter { case (_, entries) => entries.size > 1 }
      .map { case (date, items) => date -> items.size }

    duplicateDates shouldBe Map.empty
  }
}
