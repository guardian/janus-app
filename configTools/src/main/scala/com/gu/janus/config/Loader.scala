package com.gu.janus.config

import cats.implicits.*
import com.gu.janus.model.*
import com.gu.janus.model.given
import com.typesafe.config.Config
import io.circe.config.syntax.*
import io.circe.generic.auto.*

import scala.util.Try

/** Loads an instance of JanusData from a Typesafe Config definition. If it
  * fails, a description of the failure is made available.
  */
object Loader {
  def fromConfig(config: Config): Either[String, JanusData] = {
    for {
      permissionsRepo <- loadPermissionsRepo(config)
      accounts <- loadAccounts(config)
      permissions <- loadPermissions(config, accounts)
      access <- loadAccess(config, permissions)
      admin <- loadAdmin(config, permissions)
      support <- loadSupport(config, permissions)
    } yield JanusData(accounts, access, admin, support, permissionsRepo)
  }

  private[config] def loadPermissionsRepo(
      config: Config
  ): Either[String, Option[String]] = {
    val path = "janus.permissionsRepo"
    for {
      permissionsRepo <-
        if (config.hasPath(path)) {
          Try(Option(config.getString(path))).toEither.left.map(_.getMessage)
        } else {
          Right(None)
        }
    } yield permissionsRepo
  }

  private[config] def loadAccounts(
      config: Config
  ): Either[String, Set[AwsAccount]] = {
    for {
      configuredAccounts <- config
        .as[ConfiguredAccounts]("janus")
        .left
        .map(_.getMessage)
    } yield {
      configuredAccounts.accounts.map { configuredAccount =>
        AwsAccount(
          name = configuredAccount.name,
          authConfigKey = configuredAccount.key
        )
      }.toSet
    }
  }

  private[config] def loadPermissions(
      config: Config,
      accounts: Set[AwsAccount]
  ): Either[String, Set[Permission]] = {
    for {
      configuredPermissions <- config
        .as[ConfiguredPermissions]("janus")
        .left
        .map(err =>
          s"Failed to load permissions from path `janus`: ${err.getMessage}"
        )
      permissions <- configuredPermissions.permissions.traverse {
        configuredPermission =>
          for {
            account <- accounts
              .find(_.authConfigKey == configuredPermission.account)
              .toRight(
                s"Account `${configuredPermission.account}` is referenced in a permission (${configuredPermission.label}) but is not defined in the list of AwsAccounts"
              )
          } yield {
            Permission(
              account = account,
              label = configuredPermission.label,
              description = configuredPermission.description,
              policy = configuredPermission.policy,
              managedPolicyArns = configuredPermission.managedPolicyArns,
              shortTerm = configuredPermission.shortTerm
            )
          }
      }
    } yield permissions.toSet
  }

  private[config] def loadAccess(
      config: Config,
      permissions: Set[Permission]
  ): Either[String, ACL] = {
    for {
      configuredAccess <- config
        .as[ConfiguredAccess]("janus.access")
        .left
        .map(err =>
          s"Failed to load access from path `janus.access`: ${err.getMessage}"
        )
      defaultAccess <- configuredAccess.defaultPermissions.traverse {
        configuredAclEntry =>
          permissions
            .find(p =>
              configuredAclEntry.account == p.account.authConfigKey && configuredAclEntry.label == p.label
            )
            .toRight(
              s"The 'default permissions' section of the access definition includes a permission that doesn't appear to be defined.\nIt has label `${configuredAclEntry.label}` and refers to the account with key ${configuredAclEntry.account}"
            )
      }
      acl <- parseAclEntries(configuredAccess.acl, permissions)
    } yield ACL(acl.toMap, defaultAccess.toSet)
  }

  private[config] def loadAdmin(
      config: Config,
      permissions: Set[Permission]
  ): Either[String, ACL] = {
    for {
      configuredAccess <- config
        .as[ConfiguredAdmin]("janus.admin")
        .left
        .map(err =>
          s"Failed to load admin config from path `janus.admin`: ${err.getMessage}"
        )
      acl <- parseAclEntries(configuredAccess.acl, permissions)
    } yield ACL(
      acl.toMap,
      Set.empty
    ) // TODO: these shouldn't share a representation since Admin doesn't need the default permissions
  }

  private def parseAclEntries(
      acl: Map[String, List[ConfiguredAclEntry | ConfiguredRoleAclEntry]],
      permissions: Set[Permission]
  ): Either[String, List[(String, Set[Permission | ProvisionedRole])]] =
    acl.toList.traverse { case (username, configuredAclEntries) =>
      for {
        userPermissions <- configuredAclEntries.traverse {

          case configuredAclEntry: ConfiguredAclEntry =>
            permissions
              .find(p =>
                configuredAclEntry.account == p.account.authConfigKey && configuredAclEntry.label == p.label
              )
              .map(p => p: Permission | ProvisionedRole)
              .toRight(
                s"The access configuration for `$username` includes a permission that doesn't appear to be defined.\nIt has label `${configuredAclEntry.label}` and refers to the account with key ${configuredAclEntry.account}"
              )

          case entry: ConfiguredRoleAclEntry =>
            permissions
              .find(p => entry.account == p.account.authConfigKey)
              .map(p =>
                ProvisionedRole(p.account, entry.iamRoleTag): Permission |
                  ProvisionedRole
              )
              .toRight(
                s"The access configuration for `$username` includes an account with key ${entry.account} that doesn't appear to be defined."
              )
        }
      } yield username -> userPermissions.toSet
    }

  private[config] def loadSupport(
      config: Config,
      permissions: Set[Permission]
  ): Either[String, SupportACL] = {
    for {
      configuredSupport <- config
        .as[ConfiguredSupport]("janus.support")
        .left
        .map(err =>
          s"Failed to load support config from path `janus.support`: ${err.getMessage}"
        )
      supportAccess <- configuredSupport.supportAccess.traverse {
        configuredAclEntry =>
          permissions
            .find(p =>
              configuredAclEntry.account == p.account.authConfigKey && configuredAclEntry.label == p.label
            )
            .toRight(
              s"The support access definition includes a permission that doesn't appear to be defined.\nIt has label `${configuredAclEntry.label}` and refers to the account with key ${configuredAclEntry.account}"
            )
      }
      rota <- configuredSupport.rota.traverse {
        case ConfiguredRotaEntry(startTime, user1 :: user2 :: Nil) =>
          Right(startTime -> (user1, user2))
        case ConfiguredRotaEntry(startTime, users) =>
          Left(
            s"The support rota expects precisely 2 users, but for $startTime it has been defined with ${users
                .mkString("`", ", ", "`")}"
          )
      }
    } yield SupportACL.create(rota.toMap, supportAccess.toSet)
  }
}
