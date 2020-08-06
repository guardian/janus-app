package com.example

import Policies.AccountExtensions
import com.gu.janus.model.{Permission, SupportACL}
import org.joda.time.{DateTime, DateTimeZone, Period}


object Support {
  import Accounts._

  // helper for adding half a rota
  val tbd = ""

  private val supportPeriod = Period.weeks(1)

  /**
    * Support rota entries.
    *
    * Entries should put the start date as the date they start support.
    * Each entry lasts one week and the rota switches over at 11am.
    *
    * Entries are of the form:
    * (year, month, day) -> ("support.user1", "support.user2")
    * Avoid leading zeroes on months and days.
    * If only one support user is known for the week, use tbd (no quotes) for the other.
    *
    * Feel free to leave old rotas here for a while to preserve an easily
    * visible log of who had support access during past weeks.
    */
  val rota: List[((Int, Int, Int), (String, String))] = List(
    (1970, 1, 6)   -> ("sherlock.holmes", "john.watson"),
    (1970, 1, 13)  -> ("irene.adler", "sherlock.holmes"),
    (1970, 1, 20)  -> ("john.watson", "irene.adler"),
    (1970, 1, 27)  -> (tbd, tbd)
  )

  // the following accounts are covered by 24/7 support
  private val supportAccounts = Set(Production)
  private val supportAccess: Set[Permission] = supportAccounts.flatMap(_.dev)

  val acl: SupportACL = SupportACL.create(rota.toMap map convertRota, supportAccess, supportPeriod)

  /**
    * Helper so the rota can be simply hand-written.
    */
  private def convertRota(rotaEntry: ((Int, Int, Int), (String, String))): (DateTime, (String, String)) = {
    rotaEntry match { case ((year, month, day), users) =>
      // this rota changes over at 11am London time
      new DateTime(year, month, day, 11, 0, DateTimeZone.forID("Europe/London")) -> users
    }
  }
}
