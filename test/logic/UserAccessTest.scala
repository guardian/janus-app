package logic

import awscala.DateTime
import com.gu.googleauth.UserIdentity
import fixtures.Fixtures._
import com.gu.janus.model.{ACL, SupportACL}
import org.joda.time.{DateTimeZone, Period}
import org.scalatest.{FreeSpec, Inspectors, Matchers, OptionValues}
import testutils.JodaTimeUtils


class UserAccessTest extends FreeSpec with Matchers with OptionValues with Inspectors with JodaTimeUtils {
  import UserAccess._

  "userAccess" - {
    val testAccess = ACL(Map("test.user" -> Set(fooDev, barDev)), Set(bazDev, quxDev))

    "returns None if the user doesn't have any permissions" in {
      userAccess("username.does.not.exist", testAccess) should equal(None)
    }

    "returns the user's permissions if they exist" in {
      userAccess("test.user", testAccess).value should contain allOf (fooDev, barDev)
    }

    "include default permissions in all users' available permissions" in {
      val access = userAccess("test.user", testAccess).value
      testAccess.defaultPermissions foreach { perm =>
        access should contain(perm)
      }
    }

    "deduplicates a user's permissions" in {
      val permissions = Set(fooDev, barDev, fooDev, barDev)
      val access = ACL(Map("test.user" -> permissions), Set.empty)
      userAccess("test.user", access).value shouldEqual (permissions ++ access.defaultPermissions)
    }
  }

  "hasAccess" - {
    val adminACL = ACL(Map("test.user" -> Set(fooDev, barDev)), allTestPerms)

    "returns true when given a user that has an entry" in {
      hasAccess("test.user", adminACL) shouldEqual true
    }

    "returns false if the user is not explicitly mentioned" in {
      hasAccess("not.in.the.list", adminACL) shouldEqual false
    }
  }

  "support functions" - {
    val baseline = new DateTime(2016, 7, 19, 11, 0, DateTimeZone.forID("Europe/London"))
    val supportAcl = SupportACL.create(Map(
      baseline.minusDays(7) -> ("old.support.user", "another.support.user"), // out of date
      baseline -> ("support.user", "")  // in effect
    ), Set(fooCf, barCf), Period.weeks(1))
    val rotaTime = new DateTime(2016, 7, 22, 12, 0, DateTimeZone.forID("Europe/London"))

    "userSupportAccess" - {
      "returns support access when given a user currently on the support rota" in {
        userSupportAccess("support.user", rotaTime, supportAcl).value shouldEqual supportAcl.supportAccess
      }

      "returns None if the user is not on the support rota" in {
        userSupportAccess("not.a.support.user", rotaTime, supportAcl) shouldBe None
      }

      "returns None if the user is on the support rota, but not for this now" in {
        userSupportAccess("old.support.user", rotaTime, supportAcl) shouldBe None
      }

      "returns None for an empty username, even if it's mentioned in the rota" in {
        userSupportAccess("", rotaTime, supportAcl) shouldBe None
      }

      "around the cutoff point" - {
        "returns support access just before 11am UK time" in {
          val justBeforeCutoff = new DateTime(2016, 7, 26, 10, 59, DateTimeZone.forID("Europe/London"))
          userSupportAccess("support.user", justBeforeCutoff, supportAcl).value shouldEqual supportAcl.supportAccess
        }
        "returns None just after 11am UK time" in {
          val justAfterCutoff = new DateTime(2016, 7, 26, 11, 1, DateTimeZone.forID("Europe/London"))
          userSupportAccess("support.user", justAfterCutoff, supportAcl) shouldEqual None
        }
      }
    }

    "isSupportUser" - {
      "returns true access when given a user currently on the support rota" in {
        isSupportUser("support.user", rotaTime, supportAcl) shouldEqual true
      }

      "returns false if the user is not on the support rota" in {
        isSupportUser("not.a.support.user", rotaTime, supportAcl) shouldEqual false
      }

      "returns false if the user is on the support rota, but not for this now" in {
        isSupportUser("old.support.user", rotaTime, supportAcl) shouldEqual false
      }

      "returns false for an empty username, even if it's mentioned in the rota" in {
        isSupportUser("", rotaTime, supportAcl) shouldEqual false
      }

      "around the cutoff point" - {
        "returns true just before 11am UK time" in {
          val justBeforeCutoff = new DateTime(2016, 7, 26, 10, 59, DateTimeZone.forID("Europe/London"))
          isSupportUser("support.user", justBeforeCutoff, supportAcl) shouldEqual true
        }

        "returns false just after 11am UK time" in {
          val justAfterCutoff = new DateTime(2016, 7, 26, 11, 1, DateTimeZone.forID("Europe/London"))
          isSupportUser("support.user", justAfterCutoff, supportAcl) shouldEqual false
        }
      }
    }

    "can check which users have support access" - {
      val rotaStartTime = new DateTime(2016, 10, 11, 11, 0, DateTimeZone.forID("Europe/London")).withZone(DateTimeZone.UTC)
      val currentTime = new DateTime(2016, 10, 11, 12, 0, DateTimeZone.forID("Europe/London")).withZone(DateTimeZone.UTC)
      def testActiveSupportAcl(user1: String, user2: String): SupportACL = {
        SupportACL.create(
          Map(rotaStartTime -> (user1, user2)),
          Set(fooCf, barCf),
          Period.weeks(1)
        )
      }

      "activeSupportUsers" - {
        "returns the correct active users" in {
          val acl = testActiveSupportAcl("user.1", "user.2")
          val (_, (user1, user2)) = activeSupportUsers(currentTime, acl).value
          user1.value shouldEqual "user.1"
          user2.value shouldEqual "user.2"
        }

        "returns None for a user that has an empty username (means they are still tbd)" in {
          val acl = testActiveSupportAcl("", "")
          val (_, (user1, user2)) = activeSupportUsers(currentTime, acl).value
          user1 shouldEqual None
          user2 shouldEqual None
        }

        "returns None if there are no entries for today's date" in {
          val acl = testActiveSupportAcl("user.1", "user.2")
          activeSupportUsers(currentTime.minusDays(20), acl) shouldEqual None
        }

        "returns the date the rota started at" in {
          val acl = testActiveSupportAcl("user.1", "user.2")
          val (startTime, _) = activeSupportUsers(currentTime, acl).value
          startTime shouldEqual rotaStartTime
        }
      }

      "nextSupportUsers" - {
        val currentTimeForNextRota = currentTime.minusDays(7)

        "returns the correct users for the next rota" in {
          val acl = testActiveSupportAcl("user.1", "user.2")
          val (_, (user1, user2)) = nextSupportUsers(currentTimeForNextRota, acl).value
          user1.value shouldEqual "user.1"
          user2.value shouldEqual "user.2"
        }

        "returns None for a user that has an empty username (means they are still tbd)" in {
          val acl = testActiveSupportAcl("", "")
          val (_, (user1, user2)) = nextSupportUsers(currentTimeForNextRota, acl).value
          user1 shouldEqual None
          user2 shouldEqual None
        }

        "returns None if there are no entries for the next rota by provided date" in {
          val acl = testActiveSupportAcl("user.1", "user.2")
          nextSupportUsers(currentTimeForNextRota.minusDays(20), acl) shouldEqual None
        }

        "returns the date the rota started at" in {
          val acl = testActiveSupportAcl("user.1", "user.2")
          val (startTime, _) = nextSupportUsers(currentTimeForNextRota, acl).value
          startTime shouldEqual rotaStartTime
        }
      }

      "userSupportSlots" - {
        val supportAcl = SupportACL.create(Map(
          rotaStartTime -> ("userA", "userB"),
          rotaStartTime.plusWeeks(1) -> ("userC", "userD"),
          rotaStartTime.plusWeeks(2) -> ("user1", "user2"),
          rotaStartTime.plusWeeks(3) -> ("user3", "user4"),
          rotaStartTime.plusWeeks(4) -> ("user5", "user1"),
          rotaStartTime.plusWeeks(5) -> ("user2", "user4"),
          rotaStartTime.plusWeeks(6) -> ("user5", "user3")
        ), Set(fooCf, barCf), Period.weeks(1))

        "returns the correct set of future rota slots for user1 from currentTime" in {
          val slots = futureRotaSlotsForUser(currentTime, supportAcl, "user1")
          slots shouldEqual List(
            (rotaStartTime.plusWeeks(2), "user2"),
            (rotaStartTime.plusWeeks(4), "user5")
          )
        }

        "returns the correct set of future rota slots for user1 from currentTime+2w" in {
          val slots = futureRotaSlotsForUser(currentTime.plusWeeks(2), supportAcl, "user1")
          slots shouldEqual List((rotaStartTime.plusWeeks(4), "user5"))
        }

        "returns the correct set of future rota slots for user2 from currentTime+2w" in {
          val slots = futureRotaSlotsForUser(currentTime.plusWeeks(2), supportAcl, "user2")
          slots shouldEqual List((rotaStartTime.plusWeeks(5), "user4"))
        }

        "returns no slots for userA" in {
          val slots = futureRotaSlotsForUser(currentTime, supportAcl, "userA")
          slots.isEmpty shouldBe true
        }
      }
    }
  }

  "checkUserPermission" - {
    val acl = ACL(Map(
      "user" -> Set(fooDev)
    ), Set.empty)
    val adminAcl = ACL(Map("admin" -> allTestPerms))
    val supportAcl = SupportACL.create(Map(
      DateTime.now().minusDays(1) -> ("support.user", "another.support.user")
    ), allTestPerms, Period.weeks(1))

    "returns the permission if a user has been granted access" in {
      checkUserPermission("user", fooDev.id, DateTime.now(), acl, adminAcl, supportAcl).value shouldEqual fooDev
    }

    "returns the permission if it has been granted via admin access" in {
      forAll(allTestPerms) { adminPermission =>
        checkUserPermission("admin", adminPermission.id, DateTime.now(), acl, adminAcl, supportAcl).value shouldEqual adminPermission
      }
    }

    "returns the permission if it has been granted via support access" in {
      forAll(supportAcl.supportAccess) { supportPermission =>
        checkUserPermission("support.user", supportPermission.id, DateTime.now(), acl, adminAcl, supportAcl).value shouldEqual supportPermission
      }
    }

    "returns None if the permission has not been granted to the user" in {
      checkUserPermission("no.permissions", fooDev.id, DateTime.now(), acl, adminAcl, supportAcl) shouldBe None
    }
  }

  "hasExplicitAccess" - {
    val acl = ACL(Map(
      "user" -> Set(fooDev)
    ), Set.empty)
    val adminAcl = ACL(Map("admin" -> Set(fooDev)))
    val supportAcl = SupportACL.create(Map(
      DateTime.now().minusDays(1) -> ("support.user", "another.support.user")
    ), Set(fooDev), Period.weeks(1))

    "returns true if a user has been granted explicit access" in {
      hasExplicitAccess("user", fooDev, acl) shouldEqual true
    }

    "returns false if an admin user does not have explicit access" in {
      hasExplicitAccess("admin", fooDev, acl) shouldEqual false
    }

    "returns false if a support user does not have explicit access" in {
      hasExplicitAccess("support.user", fooDev, acl) shouldEqual false
    }
  }

  "userAccountAccess" - {
    val acl = ACL(Map(
      "user" -> Set(fooDev),
      "admin" -> Set.empty,
      "support.user" -> Set.empty
    ), Set.empty)
    val admins = ACL(Map("admin" -> Set(fooCf, barDev)))
    val supportAcl = SupportACL.create(Map(
      DateTime.now().minusDays(1) -> ("support.user", "another.support.user")
    ), Set(fooCf, barDev), Period.weeks(1))

    "returns permissions if a user has been granted explicit access" in {
      userAccountAccess("user", fooAct.authConfigKey, DateTime.now(), acl, admins, supportAcl) shouldEqual Set(fooDev)
    }

    "returns permissions for an admin user without explicit access to the account" in {
      userAccountAccess("admin", fooAct.authConfigKey, DateTime.now(), acl, admins, supportAcl) shouldEqual Set(fooCf)
    }

    "returns permissions for a support user without explicit access to a support account" in {
      userAccountAccess("support.user", fooAct.authConfigKey, DateTime.now(), acl, admins, supportAcl) shouldEqual Set(fooCf)
    }

    "returns empty permissions for a non-admin, non-support user that does not have explicit access" in {
      userAccountAccess("user", barAct.authConfigKey, DateTime.now(), acl, admins, supportAcl) shouldBe empty
    }
  }

  "username" - {
    "uses the email address, not first name and last name (which doesn't work for i18n names)" in {
      username(UserIdentity("sub", "first.last@example.com", "First", "Last One Two", 86400, None)) shouldEqual "first.last"
    }

    "lower-cases the provided email address" in {
      username(UserIdentity("sub", "First.Last@example.com", "First", "Last One Two", 86400, None)) shouldEqual "first.last"
    }
  }
}
