package com.gu.janus.model

import io.circe.Decoder

import java.time.Instant

case class ConfiguredAccount(
    name: String,
    key: String
) derives Decoder

// helps circe-config auto-extract data
case class ConfiguredAccounts(
    accounts: List[ConfiguredAccount]
) derives Decoder

case class ConfiguredPermission(
    account: String,
    label: String,
    description: String,
    policy: Option[String],
    managedPolicyArns: Option[List[String]],
    shortTerm: Boolean = false,
    // this field is optional in the config representation to maintain backwards compatibility
    sessionType: Option[String] = Some("user")
) derives Decoder

// helps circe-config auto-extract data
case class ConfiguredPermissions(
    permissions: List[ConfiguredPermission]
) derives Decoder

case class ConfiguredPermissionReference(
    account: String,
    label: String
) derives Decoder

case class ConfiguredRoles(
    roles: List[ConfiguredRole]
) derives Decoder

case class ConfiguredRole(
    name: String,
    permissions: List[ConfiguredPermissionReference]
) derives Decoder

case class ConfiguredRoleReference(
    name: String
) derives Decoder

case class ConfiguredAccess(
    defaultPermissions: List[ConfiguredPermissionReference],
    acl: Map[
      String,
      ConfiguredAclEntry
    ]
) derives Decoder

case class ConfiguredAclEntry(
    permissions: List[ConfiguredPermissionReference],
    roles: List[ConfiguredRoleReference]
) derives Decoder

// helps circe-config auto-extract data
case class ConfiguredAdmin(
    acl: Map[String, ConfiguredAclEntry]
) derives Decoder

case class ConfiguredSupport(
    supportAccess: List[ConfiguredPermissionReference],
    rota: List[ConfiguredRotaEntry],
    period: Int
) derives Decoder

case class ConfiguredRotaEntry(
    startTime: Instant,
    supporting: List[String]
) derives Decoder
