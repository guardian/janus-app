package com.gu.janus.model

import java.time.Instant

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
    policy: Option[String],
    managedPolicyArns: Option[List[String]],
    shortTerm: Boolean = false,
    // this field is optional in the config representation to maintain backwards compatibility
    sessionType: Option[String] = Some("user")
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
    startTime: Instant,
    supporting: List[String]
)
