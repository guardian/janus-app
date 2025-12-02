package com.gu.janus.model

import cats.implicits.toFunctorOps
import io.circe.Decoder
import io.circe.generic.auto.deriveDecoder

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

case class ConfiguredRoleAclEntry(
    account: String,
    iamRoleTag: String
)

given Decoder[ConfiguredAclEntry | ConfiguredRoleAclEntry] =
  Decoder[ConfiguredAclEntry]
    .widen[ConfiguredAclEntry | ConfiguredRoleAclEntry]
    .or(
      Decoder[ConfiguredRoleAclEntry]
        .widen[ConfiguredAclEntry | ConfiguredRoleAclEntry]
    )

case class ConfiguredAccess(
    defaultPermissions: List[ConfiguredAclEntry],
    acl: Map[String, List[ConfiguredAclEntry | ConfiguredRoleAclEntry]]
)

// helps circe-config auto-extract data
case class ConfiguredAdmin(
    acl: Map[String, List[ConfiguredAclEntry | ConfiguredRoleAclEntry]]
)

case class ConfiguredSupport(
    supportAccess: List[ConfiguredAclEntry],
    rota: List[ConfiguredRotaEntry]
)

case class ConfiguredRotaEntry(
    startTime: Instant,
    supporting: List[String]
)
