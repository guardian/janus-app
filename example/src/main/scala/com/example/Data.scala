package com.example

import com.gu.janus.model.JanusData

object Data {
  val janusData = JanusData(
    accounts = Accounts.allAccounts,
    access = Access.acl,
    superuser = Superuser.acl,
    support = Support.acl,
    permissionsRepo = None
  )
}
