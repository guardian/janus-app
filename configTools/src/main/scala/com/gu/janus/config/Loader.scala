package com.gu.janus.config

import cats.implicits._
import com.gu.janus.model._
import com.typesafe.config.Config
import io.circe.Decoder
import io.circe.config.syntax._
import io.circe.generic.auto._
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone, Period}

import scala.util.control.NonFatal


/**
  * Loads an instance of JanusData from a Typesafe Config definition.
  * If it fails, a description of the failure is made available.
  */
object Loader {
  def fromConfig(config: Config): Either[String, JanusData] = {
    for {
      accounts <- loadAccounts(config)
      permissions <- loadPermissions(config, accounts)
      access <- loadAccess(config, permissions)
      admin <- loadAdmin(config, permissions)
      support <- loadSupport(config, permissions)
    } yield JanusData(accounts, access, admin, support)
  }

  private[config] def loadAccounts(config: Config): Either[String, Set[AwsAccount]] = {
    for {
      configuredAccounts <- config.as[ConfiguredAccounts]("janus").left.map(_.getMessage)
    } yield {
      configuredAccounts.accounts.map { configuredAccount =>
        AwsAccount(
          name = configuredAccount.name,
          authConfigKey = configuredAccount.key
        )
      }.toSet
    }
  }

  private[config] def loadPermissions(config: Config, accounts: Set[AwsAccount]): Either[String, Set[Permission]] = {
    for {
      configuredPermissions <- config.as[ConfiguredPermissions]("janus").left.map(err => s"Failed to load permissions from path `janus`: ${err.getMessage}")
      permissions <- configuredPermissions.permissions.traverse[Either[String, ?], Permission] { configuredPermission =>
        for {
          account <- accounts.find(_.authConfigKey == configuredPermission.account)
            .toRight(s"Account `${configuredPermission.account}` is referenced in a permission (${configuredPermission.label}) but is not defined in the list of AwsAccounts")
        } yield {
          Permission(
            account = account,
            label = configuredPermission.label,
            description = configuredPermission.description,
            policy = configuredPermission.policy,
            shortTerm = configuredPermission.shortTerm
          )
        }
      }
    } yield permissions.toSet
  }

  private[config] def loadAccess(config: Config, permissions: Set[Permission]): Either[String, ACL] = {
    for {
      configuredAccess <- config.as[ConfiguredAccess]("janus.access").left.map(err => s"Failed to load access from path `janus.access`: ${err.getMessage}")
      defaultAccess <- configuredAccess.defaultPermissions.traverse[Either[String, ?], Permission] { configuredAclEntry =>
        permissions.find(p => configuredAclEntry.account == p.account.authConfigKey && configuredAclEntry.label == p.label)
          .toRight(s"The 'default permissions' section of the access definition includes a permission that doesn't appear to be defined.\nIt has label `${configuredAclEntry.label}` and refers to the account with key ${configuredAclEntry.account}")
      }
      acl <- configuredAccess.acl.toList.traverse[Either[String, ?], (String, Set[Permission])] { case (username, configuredAclEntries) =>
        for {
          userPermissions <- configuredAclEntries.traverse[Either[String, ?], Permission] { configuredAclEntry =>
            permissions.find(p => configuredAclEntry.account == p.account.authConfigKey && configuredAclEntry.label == p.label)
              .toRight(s"The access configuration for `$username` includes a permission that doesn't appear to be defined.\nIt has label `${configuredAclEntry.label}` and refers to the account with key ${configuredAclEntry.account}")
          }
        } yield username -> userPermissions.toSet
      }
    } yield ACL(acl.toMap, defaultAccess.toSet)
  }

  private[config] def loadAdmin(config: Config, permissions: Set[Permission]): Either[String, ACL] = {
    for {
      configuredAccess <- config.as[ConfiguredAdmin]("janus.admin").left.map(err => s"Failed to load admin config from path `janus.admin`: ${err.getMessage}")
      acl <- configuredAccess.acl.toList.traverse[Either[String, ?], (String, Set[Permission])] { case (username, configuredAclEntries) =>
        for {
          userPermissions <- configuredAclEntries.traverse[Either[String, ?], Permission] { configuredAclEntry =>
            permissions.find(p => configuredAclEntry.account == p.account.authConfigKey && configuredAclEntry.label == p.label)
              .toRight(s"The admin configuration for `$username` includes a permission that doesn't appear to be defined.\nIt has label `${configuredAclEntry.label}` and refers to the account with key ${configuredAclEntry.account}")
          }
        } yield username -> userPermissions.toSet
      }
    } yield ACL(acl.toMap, Set.empty)  // TODO: these shouldn't share a representation since Admin doesn't need the default permissions
  }

  private[config] def loadSupport(config: Config, permissions: Set[Permission]): Either[String, SupportACL] = {
    for {
      configuredSupport <- config.as[ConfiguredSupport]("janus.support").left.map(err => s"Failed to load support config from path `janus.support`: ${err.getMessage}")
      supportAccess <- configuredSupport.supportAccess.traverse[Either[String, ?], Permission] { configuredAclEntry =>
        permissions.find(p => configuredAclEntry.account == p.account.authConfigKey && configuredAclEntry.label == p.label)
          .toRight(s"The support access definition includes a permission that doesn't appear to be defined.\nIt has label `${configuredAclEntry.label}` and refers to the account with key ${configuredAclEntry.account}")
      }
      period = Period.seconds(configuredSupport.period)
      rota <- configuredSupport.rota.traverse[Either[String, ?], (DateTime, (String, String))] {
        case ConfiguredRotaEntry(startTime, user1 :: user2 :: Nil) =>
          Right(startTime -> (user1, user2))
        case ConfiguredRotaEntry(startTime, users) =>
          Left(s"The support rota expects precisely 2 users, but for $startTime it has been defined with ${users.mkString("`", ", ", "`")}")
      }
    } yield SupportACL.create(rota.toMap, supportAccess.toSet, period)
  }

  private implicit val decodeDateTime: Decoder[DateTime] = Decoder.decodeString.emap { s =>
    try {
      Right(DateTime.parse(s, ISODateTimeFormat.dateTime()).withZone(DateTimeZone.UTC))
    } catch {
      case NonFatal(e) => Left(e.getMessage)
    }
  }
}
