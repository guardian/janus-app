package logic

import com.gu.janus.model.*
import fixtures.Fixtures.*
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import java.time.*

class SupportUserAccessTest
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaCheckDrivenPropertyChecks {

  import SupportUserAccess.*

  "support functions" - {
    val baseline = ZonedDateTime
      .of(2016, 7, 19, 11, 0, 0, 0, ZoneId.of("Europe/London"))
      .toInstant
    val supportAcl = SupportACL.create(
      Map(
        baseline.minus(Duration.ofDays(7)) -> (
          "old.support.user",
          "another.support.user"
        ), // out of date
        baseline -> ("support.user", ""), // in effect
        baseline.plus(Duration.ofDays(7)) -> (
          "next.support.user",
          ""
        ) // next slot
      ),
      Set(fooCf, barCf)
    )
    val rotaTime = ZonedDateTime
      .of(2016, 7, 22, 12, 0, 0, 0, ZoneId.of("Europe/London"))
      .toInstant

    "userSupportAccess" - {
      "returns support access when given a user currently on the support rota" in {
        userSupportAccess(
          "support.user",
          rotaTime,
          supportAcl
        ).value shouldEqual supportAcl.supportAccess
      }

      "returns None if the user is not on the support rota" in {
        userSupportAccess(
          "not.a.support.user",
          rotaTime,
          supportAcl
        ) shouldBe None
      }

      "returns None if the user is on the support rota, but not for this now" in {
        userSupportAccess(
          "old.support.user",
          rotaTime,
          supportAcl
        ) shouldBe None
      }

      "returns None for an empty username, even if it's mentioned in the rota" in {
        userSupportAccess("", rotaTime, supportAcl) shouldBe None
      }

      "around the cutoff point" - {
        "returns support access just before 11am UK time" in {
          val justBeforeCutoff = ZonedDateTime
            .of(2016, 7, 26, 10, 59, 0, 0, ZoneId.of("Europe/London"))
            .toInstant
          userSupportAccess(
            "support.user",
            justBeforeCutoff,
            supportAcl
          ).value shouldEqual supportAcl.supportAccess
        }
        "returns None just after 11am UK time" in {
          val justAfterCutoff = ZonedDateTime
            .of(
              2016,
              7,
              26,
              11,
              1,
              0,
              0,
              ZoneId.of("Europe/London")
            )
            .toInstant
          userSupportAccess(
            "support.user",
            justAfterCutoff,
            supportAcl
          ) shouldEqual None
        }
      }
    }

    "isSupportUser" - {
      "returns true access when given a user currently on the support rota" in {
        isSupportUser("support.user", rotaTime, supportAcl) shouldEqual true
      }

      "returns false if the user is not on the support rota" in {
        isSupportUser(
          "not.a.support.user",
          rotaTime,
          supportAcl
        ) shouldEqual false
      }

      "returns false if the user is on the support rota, but not for this now" in {
        isSupportUser(
          "old.support.user",
          rotaTime,
          supportAcl
        ) shouldEqual false
      }

      "returns false for an empty username, even if it's mentioned in the rota" in {
        isSupportUser("", rotaTime, supportAcl) shouldEqual false
      }

      "around the cutoff point" - {
        "returns true just before 11am UK time" in {
          val justBeforeCutoff = ZonedDateTime
            .of(
              2016,
              7,
              26,
              10,
              59,
              0,
              0,
              ZoneId.of("Europe/London")
            )
            .toInstant
          isSupportUser(
            "support.user",
            justBeforeCutoff,
            supportAcl
          ) shouldEqual true
        }

        "returns false just after 11am UK time" in {
          val justAfterCutoff = ZonedDateTime
            .of(
              2016,
              7,
              26,
              11,
              1,
              0,
              0,
              ZoneId.of("Europe/London")
            )
            .toInstant
          isSupportUser(
            "support.user",
            justAfterCutoff,
            supportAcl
          ) shouldEqual false
        }
      }
    }

    "can check which users have support access" - {
      val rotaStartTime =
        ZonedDateTime
          .of(2016, 10, 11, 11, 0, 0, 0, ZoneId.of("Europe/London"))
          .toInstant
      val currentTime =
        ZonedDateTime
          .of(2016, 10, 11, 12, 0, 0, 0, ZoneId.of("Europe/London"))
          .toInstant
      def testActiveSupportAcl(user1: String, user2: String): SupportACL = {
        SupportACL.create(
          Map(rotaStartTime -> (user1, user2)),
          Set(fooCf, barCf)
        )
      }

      val engineers = List("eng1", "eng2", "eng3", "eng4", "eng5")

      def genInstant(min: Instant, max: Instant) = Gen
        .choose(
          min = min.toEpochMilli,
          max = max.toEpochMilli
        )
        .map(Instant.ofEpochMilli)

      val genRota = for {
        slotCount <- Gen.choose(min = 3, max = 10)
        dates <- Gen.listOfN(
          slotCount,
          genInstant(
            min = Instant.parse("2024-06-01T01:00:00Z"),
            max = Instant.parse("2026-04-30T01:00:00Z")
          )
        )
        pairs <- Gen.listOfN(
          slotCount,
          Gen.pick(2, engineers).map(engs => (engs(0), engs(1)))
        )
      } yield dates.zip(pairs).toMap

      val rotaAndTimeBeforeRotaTimespan = for {
        rota <- genRota
        time <- genInstant(
          min = rota.keys.min.minus(Duration.ofDays(400)),
          max = rota.keys.min
        )
      } yield (SupportACL.create(rota, Set(fooDev)), time)

      val rotaAndTimeWithinRotaTimespan = for {
        rota <- genRota
        time <- genInstant(min = rota.keys.min, max = rota.keys.max)
      } yield (SupportACL.create(rota, Set(fooDev)), time)

      val rotaAndTimeAfterRotaTimespan = for {
        rota <- genRota
        time <- genInstant(
          min = rota.keys.max,
          max = rota.keys.max.plus(Duration.ofDays(400))
        )
      } yield (SupportACL.create(rota, Set(fooDev)), time)

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
          activeSupportUsers(
            currentTime.minus(Duration.ofDays(20)),
            acl
          ) shouldEqual None
        }

        "returns the date the rota started at" in {
          val acl = testActiveSupportAcl("user.1", "user.2")
          val (startTime, _) = activeSupportUsers(currentTime, acl).value
          startTime shouldEqual rotaStartTime
        }

        "there should always be active support engineers on any rota that begins before a given start time" in {
          forAll(rotaAndTimeWithinRotaTimespan) {
            case (acl: SupportACL, time: Instant) =>
              activeSupportUsers(time, acl).isDefined shouldBe true
          }
        }

        "there should never be active support engineers on any rota that begins after a given start time" in {
          forAll(rotaAndTimeBeforeRotaTimespan) {
            case (acl: SupportACL, time: Instant) =>
              activeSupportUsers(time, acl).isEmpty shouldBe true
          }
        }

        "the active support slot should always be the most recent slot on the rota" in {
          forAll(rotaAndTimeWithinRotaTimespan) {
            case (acl: SupportACL, time: Instant) =>
              val (slotStartTime, _) = activeSupportUsers(time, acl).value
              val pastStartTimes = acl.rota.keys.filter(_.isBefore(time))
              val mostRecentStartTime = pastStartTimes.max
              slotStartTime.getEpochSecond shouldBe mostRecentStartTime.getEpochSecond
          }
        }

        "the active support slot should always be the last on the rota for any given start time after the last slot" in {
          forAll(rotaAndTimeAfterRotaTimespan) {
            case (acl: SupportACL, time: Instant) =>
              val (slotStartTime, _) = activeSupportUsers(time, acl).value
              val last = acl.rota.keys.max
              slotStartTime.getEpochSecond shouldBe last.getEpochSecond
          }
        }
      }

      "nextSupportUsers" - {
        val currentTimeForNextRota = currentTime.minus(Duration.ofDays(7))

        "returns the correct users for the next rota" in {
          val acl = testActiveSupportAcl("user.1", "user.2")
          val (_, (user1, user2)) =
            nextSupportUsers(currentTimeForNextRota, acl).value
          user1.value shouldEqual "user.1"
          user2.value shouldEqual "user.2"
        }

        "returns None for a user that has an empty username (means they are still tbd)" in {
          val acl = testActiveSupportAcl("", "")
          val (_, (user1, user2)) =
            nextSupportUsers(currentTimeForNextRota, acl).value
          user1 shouldEqual None
          user2 shouldEqual None
        }

        "returns the date the rota started at" in {
          val acl = testActiveSupportAcl("user.1", "user.2")
          val (startTime, _) =
            nextSupportUsers(currentTimeForNextRota, acl).value
          startTime shouldEqual rotaStartTime
        }

        "the next support slot should always be after any given start time" in {
          forAll(rotaAndTimeWithinRotaTimespan) {
            case (acl: SupportACL, time: Instant) =>
              val (slotStartTime, _) = nextSupportUsers(time, acl).value
              slotStartTime.isAfter(time) shouldBe true
          }
        }

        "there should be no next support engineers for any given start time after the end of the rota" in {
          forAll(rotaAndTimeAfterRotaTimespan) {
            case (acl: SupportACL, time: Instant) =>
              nextSupportUsers(time, acl).isEmpty shouldBe true
          }
        }
      }

      "userSupportSlots" - {
        val supportAcl = SupportACL.create(
          Map(
            rotaStartTime -> ("userA", "userB"),
            rotaStartTime.plus(Period.ofWeeks(1)) -> ("userC", "userD"),
            rotaStartTime.plus(Period.ofWeeks(2)) -> ("user1", "user2"),
            rotaStartTime.plus(Period.ofWeeks(3)) -> ("user3", "user4"),
            rotaStartTime.plus(Period.ofWeeks(4)) -> ("user5", "user1"),
            rotaStartTime.plus(Period.ofWeeks(5)) -> ("user2", "user4"),
            rotaStartTime.plus(Period.ofWeeks(6)) -> ("user5", "user3")
          ),
          Set(fooCf, barCf)
        )

        "returns the correct set of future rota slots for user1 from currentTime" in {
          val slots = futureRotaSlotsForUser(currentTime, supportAcl, "user1")
          slots shouldEqual List(
            (rotaStartTime.plus(Period.ofWeeks(2)), "user2"),
            (rotaStartTime.plus(Period.ofWeeks(4)), "user5")
          )
        }

        "returns the correct set of future rota slots for user1 from currentTime+2w" in {
          val slots = futureRotaSlotsForUser(
            currentTime.plus(Period.ofWeeks(2)),
            supportAcl,
            "user1"
          )
          slots shouldEqual List(
            (rotaStartTime.plus(Period.ofWeeks(4)), "user5")
          )
        }

        "returns the correct set of future rota slots for user2 from currentTime+2w" in {
          val slots = futureRotaSlotsForUser(
            currentTime.plus(Period.ofWeeks(2)),
            supportAcl,
            "user2"
          )
          slots shouldEqual List(
            (rotaStartTime.plus(Period.ofWeeks(5)), "user4")
          )
        }

        "returns no slots for userA" in {
          val slots = futureRotaSlotsForUser(currentTime, supportAcl, "userA")
          slots.isEmpty shouldBe true
        }

        "should always be after the given time for all engineers" in {
          forAll(rotaAndTimeWithinRotaTimespan) {
            case (acl: SupportACL, time: Instant) =>
              engineers.forall { eng =>
                val slots = futureRotaSlotsForUser(time, acl, eng)
                slots.forall((startTime, _) => startTime.isAfter(time))
              } shouldBe true
          }
        }

        "should always be after the next slot" in {
          forAll(rotaAndTimeWithinRotaTimespan) {
            case (acl: SupportACL, time: Instant) =>
              val isAlwaysAfterNextSlot =
                nextSupportUsers(time, acl).forall((nextSlotStartTime, _) =>
                  engineers.forall { eng =>
                    val futureSlots = futureRotaSlotsForUser(time, acl, eng)
                    futureSlots.forall((startTime, _) =>
                      startTime.isAfter(nextSlotStartTime)
                    )
                  }
                )
              isAlwaysAfterNextSlot shouldBe true
          }
        }

        "should be empty for all engineers after the end of the rota" in {
          forAll(rotaAndTimeAfterRotaTimespan) {
            case (acl: SupportACL, time: Instant) =>
              engineers.forall { eng =>
                futureRotaSlotsForUser(time, acl, eng).isEmpty
              } shouldBe true
          }
        }
      }
    }
  }
}
