package com.example

import com.gu.janus.model.JanusData


object Data {
  val janusData = JanusData(
    accounts = Accounts.allAccounts,
    access = Access.acl,
    admin = Admin.acl,
    support = Support.acl
  )
}
