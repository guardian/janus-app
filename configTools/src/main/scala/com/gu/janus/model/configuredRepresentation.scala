package com.gu.janus.model

import org.joda.time.DateTime

case class ConfiguredAccount(
    name: String,
    key: String
)

// helps circe-config auto-extract data
case class ConfiguredAccounts(
    accounts: List[ConfiguredAccount]
)

case class ConfiguredPermission(
    account: String,
    label: String,
    description: String,
    policy: String,
    shortTerm: Boolean = false
)

// helps circe-config auto-extract data
case class ConfiguredPermissions(
    permissions: List[ConfiguredPermission]
)

case class ConfiguredAclEntry(
    account: String,
    label: String
)

case class ConfiguredAccess(
    defaultPermissions: List[ConfiguredAclEntry],
    acl: Map[String, List[ConfiguredAclEntry]]
)

// helps circe-config auto-extract data
case class ConfiguredAdmin(
    acl: Map[String, List[ConfiguredAclEntry]]
)

case class ConfiguredSupport(
    supportAccess: List[ConfiguredAclEntry],
    rota: List[ConfiguredRotaEntry],
    period: Int
)

case class ConfiguredRotaEntry(
    startTime: DateTime,
    supporting: List[String]
)
