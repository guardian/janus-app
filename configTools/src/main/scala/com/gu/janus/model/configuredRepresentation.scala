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

case class ConfiguredDeveloperPolicyGrantAclEntry(
    grantName: String,
    grantId: String,
    shortTerm: Boolean = false
)

// Makes it easier to roll back because decoding should work regardless of whether new fields are present or not
given Decoder[ConfiguredDeveloperPolicyGrantAclEntry] =
  Decoder.forProduct3(
    "grantName",
    "grantId",
    "shortTerm"
  ) { (grantName: String, grantId: String, shortTerm: Option[Boolean]) =>
    ConfiguredDeveloperPolicyGrantAclEntry(
      grantName,
      grantId,
      shortTerm.getOrElse(false)
    )
  }(
    Decoder[String],
    Decoder[String],
    Decoder[Option[Boolean]]
  )

given Decoder[ConfiguredAclEntry | ConfiguredDeveloperPolicyGrantAclEntry] =
  Decoder[ConfiguredAclEntry]
    .widen[ConfiguredAclEntry | ConfiguredDeveloperPolicyGrantAclEntry]
    .or(
      Decoder[ConfiguredDeveloperPolicyGrantAclEntry]
        .widen[ConfiguredAclEntry | ConfiguredDeveloperPolicyGrantAclEntry]
    )

case class ConfiguredAccess(
    defaultPermissions: List[ConfiguredAclEntry],
    acl: Map[String, List[
      ConfiguredAclEntry | ConfiguredDeveloperPolicyGrantAclEntry
    ]]
)

// helps circe-config auto-extract data
case class ConfiguredAdmin(
    acl: Map[String, List[
      ConfiguredAclEntry | ConfiguredDeveloperPolicyGrantAclEntry
    ]]
)

case class ConfiguredSupport(
    supportAccess: List[ConfiguredAclEntry],
    rota: List[ConfiguredRotaEntry]
)

case class ConfiguredRotaEntry(
    startTime: Instant,
    supporting: List[String]
)
