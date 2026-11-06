package com.example

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class AccessTest extends AnyFreeSpec with Matchers {
  "User access list should contain no duplicate entries" in {
    val duplicatedUsers = Access.users
      .groupBy { case (username, access) => username }
      .filter { case (username, accessEntries) =>
        accessEntries.size > 1
      }
      .map { case (username, _) => username }
    duplicatedUsers shouldEqual List()
  }

  "User access list should contain only valid username formats" in {
//  Username pattern only allows:
//    - At least two "name parts"
//    - Separated by dots
//    - Not ending in dot
//  Where "name part" is defined as alphanumeric plus dash. Name part can start or end with dash for simplicity.
    val usernamePattern = "^([a-zA-Z0-9-]+\\.[a-zA-Z0-9-]+)+$".r

    Access.users.foreach { case (username, _) =>
      withClue(
        s"username {$username} should match regex ${usernamePattern.regex}"
      ) {
        usernamePattern.findFirstIn(username) shouldBe defined
      }
    }
  }
}
