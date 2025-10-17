package com.gu.janus.config

import cats.implicits.*
import com.gu.janus.model.*
import com.typesafe.config.Config
import io.circe.Decoder
import io.circe.config.syntax.*
import io.circe.generic.auto.*

import java.time.{Duration, Instant, ZoneId, ZonedDateTime, format}
import scala.util.Try
import scala.util.control.NonFatal

/** Loads an instance of JanusData from a Typesafe Config definition. If it
  * fails, a description of the failure is made available.
  */
object Loader {
  def fromConfig(config: Config): Either[String, JanusData] = {
    for {
      permissionsRepo <- loadPermissionsRepo(config)
      accounts <- loadAccounts(config)
      permissions <- loadPermissions(config, accounts)
      roles <- loadRoles(config, permissions)
      access <- loadAccess(config, permissions, roles)
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
            sessionType <- configuredPermission.sessionType match {
              case Some("user") =>
                Right(SessionType.User)
              case Some("workload") =>
                Right(SessionType.Workload)
              case Some(unexpectedSessionType) =>
                Left(
                  s"Unknown session type '$unexpectedSessionType' in permission ${configuredPermission.label}"
                )
              case None => Right(SessionType.User)
            }
          } yield {
            Permission(
              account = account,
              label = configuredPermission.label,
              description = configuredPermission.description,
              policy = configuredPermission.policy,
              managedPolicyArns = configuredPermission.managedPolicyArns,
              shortTerm = configuredPermission.shortTerm,
              sessionType = sessionType
            )
          }
      }
    } yield permissions.toSet
  }

  private[config] def loadRoles(
      config: Config,
      permissions: Set[Permission]
  ): Either[String, Set[Role]] = {
    for {
      configuredRoles <- config
        .as[ConfiguredRoles]("janus")
        .left
        .map(err =>
          s"Failed to load roles from path `janus`: ${err.getMessage}"
        )
      // resolve all the permissions in each role here, or fail
      roles <- configuredRoles.roles.traverse { configuredRole =>
        for {
          rolePermissions <- configuredRole.permissions.traverse {
            configuredPermissionReference =>
              permissions
                .find(p =>
                  configuredPermissionReference.account == p.account.authConfigKey && configuredPermissionReference.label == p.label
                )
                .toRight(
                  s"The 'roles' configuration for role `${configuredRole.name}` includes a permission that doesn't appear to be defined.\nIt has label `${configuredPermissionReference.label}` and refers to the account with key ${configuredPermissionReference.account}"
                )
          }
        } yield Role(configuredRole.name, rolePermissions.toSet)
      }
    } yield roles.toSet
  }

  private[config] def loadAccess(
      config: Config,
      permissions: Set[Permission],
      roles: Set[Role]
  ): Either[String, ACL] = {
    for {
      configuredAccess <- config
        .as[ConfiguredAccess]("janus.access")
        .left
        .map(err =>
          s"Failed to load access from path `janus.access`: ${err.getMessage}"
        )
      defaultAccess <- configuredAccess.defaultPermissions.traverse {
        configuredPermissionReference =>
          permissions
            .find(p =>
              configuredPermissionReference.account == p.account.authConfigKey && configuredPermissionReference.label == p.label
            )
            .toRight(
              s"The 'default permissions' section of the access definition includes a permission that doesn't appear to be defined.\nIt has label `${configuredPermissionReference.label}` and refers to the account with key ${configuredPermissionReference.account}"
            )
      }
      acl <- configuredAccess.acl.toList.traverse {
        case (
              username,
              configuredAclEntries
            ) =>
          for {
            userPermissions <- configuredAclEntries.permissions
              .traverse { configuredPermissionReference =>
                permissions
                  .find(p =>
                    configuredPermissionReference.account == p.account.authConfigKey && configuredPermissionReference.label == p.label
                  )
                  .toRight(
                    s"The access configuration for `$username` includes a permission that doesn't appear to be defined.\nIt has label `${configuredPermissionReference.label}` and refers to the account with key ${configuredPermissionReference.account}"
                  )
              }
            userRoles <- configuredAclEntries.roles.traverse {
              configuredRoleReference =>
                roles
                  .find(_.name == configuredRoleReference.name)
                  .toRight(
                    s"The access configuration for `$username` includes a role `$configuredRoleReference` that isn't defined."
                  )
            }
          } yield username -> ACLEntry(userPermissions.toSet, userRoles.toSet)
      }
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
      acl <- configuredAccess.acl.toList.traverse {
        case (username, configuredAclEntries) =>
          for {
            userPermissions <- configuredAclEntries.permissions
              .traverse { configuredPermissionReference =>
                permissions
                  .find(p =>
                    configuredPermissionReference.account == p.account.authConfigKey && configuredPermissionReference.label == p.label
                  )
                  .toRight(
                    s"The admin configuration for `$username` includes a permission that doesn't appear to be defined.\nIt has label `${configuredPermissionReference.label}` and refers to the account with key ${configuredPermissionReference.account}"
                  )
              }
          } yield username -> ACLEntry(userPermissions.toSet, Set.empty[Role])
      }
    } yield ACL(
      acl.toMap,
      Set.empty
    ) // TODO: maybe these shouldn't share a representation since Admin doesn't need the default permissions or "roles"
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
        configuredPermissionReference =>
          permissions
            .find(p =>
              configuredPermissionReference.account == p.account.authConfigKey && configuredPermissionReference.label == p.label
            )
            .toRight(
              s"The support access definition includes a permission that doesn't appear to be defined.\nIt has label `${configuredPermissionReference.label}` and refers to the account with key ${configuredPermissionReference.account}"
            )
      }
      period = Duration.ofSeconds(configuredSupport.period.toLong)
      rota <- configuredSupport.rota.traverse {
        case ConfiguredRotaEntry(startTime, user1 :: user2 :: Nil) =>
          Right(startTime -> (user1, user2))
        case ConfiguredRotaEntry(startTime, users) =>
          Left(
            s"The support rota expects precisely 2 users, but for $startTime it has been defined with ${users
                .mkString("`", ", ", "`")}"
          )
      }
    } yield SupportACL.create(rota.toMap, supportAccess.toSet, period)
  }
}
