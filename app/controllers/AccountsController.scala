package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import conf.Config
import logic.Accounts
import play.api.mvc.*
import play.api.{Configuration, Logging, Mode}
import services.ProvisionedRoleStatusManager

class AccountsController(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    configuration: Configuration,
    provisionedRoleStatusManager: ProvisionedRoleStatusManager
)(using mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging {

  def accounts: Action[AnyContent] = authAction { implicit request =>
    val accountData = Accounts.accountOwnerInformation(
      provisionedRoleStatusManager.getCacheStatus,
      janusData.accounts,
      janusData.access
    )(account => Config.findAccountNumber(account.authConfigKey, configuration))

    // log any account number errors we accumulated
    Accounts
      .accountIdErrors(accountData)
      .foreach { case (account, err) =>
        logger
          .warn(
            s"Couldn't lookup account number for ${account.authConfigKey}",
            err
          )
      }
    Ok(views.html.accounts(accountData, request.user, janusData))
  }

  def accountRoles: Action[AnyContent] = authAction { implicit request =>
    val rolesStatuses = provisionedRoleStatusManager.getCacheStatus
    val accountRolesAndStatus = Accounts.getAccountRolesAndStatus(rolesStatuses)
    Ok(
      views.html
        .accountRoles(
          accountRolesAndStatus,
          request.user,
          janusData
        )
    )
  }

  def rolesStatusForAccount(authConfigKey: String): Action[AnyContent] = {

    val accountName =
      janusData.accounts.find(_.authConfigKey == authConfigKey).map(_.name)
    val rolesStatuses = provisionedRoleStatusManager.getCacheStatus
    val successfullyCreatedRoles =
      Accounts.successfulRolesForThisAccount(rolesStatuses, authConfigKey)
    val rolesWithErrors =
      Accounts.errorRolesForThisAccount(rolesStatuses, authConfigKey)

    authAction { implicit request =>
      Ok(
        views.html.rolesStatus(
          accountName.getOrElse("Unknown Account"),
          successfullyCreatedRoles,
          rolesWithErrors,
          request.user,
          janusData
        )
      )
    }
  }

  def usersForAccount(authConfigKey: String): Action[AnyContent] = {

    val accountName =
      janusData.accounts.find(_.authConfigKey == authConfigKey).map(_.name)

    val accountOwners = Accounts
      .accountOwnerInformation(
        provisionedRoleStatusManager.getCacheStatus,
        janusData.accounts,
        janusData.access
      )(account =>
        Config.findAccountNumber(account.authConfigKey, configuration)
      )
      .find(_.account.authConfigKey == authConfigKey)
      .map(_.permissions.map(_.userName))
      .getOrElse(Nil)

    authAction { implicit request =>
      Ok(
        views.html.users(
          accountName,
          accountOwners,
          request.user,
          janusData
        )
      )
    }
  }

  def accountInfo(authConfigKey: String): Action[AnyContent] = {
    val accountName =
      janusData.accounts.find(_.authConfigKey == authConfigKey).map(_.name)

    authAction { implicit request =>
      Ok(
        views.html.accountInfo(
          accountName.getOrElse("Unknown account"),
          authConfigKey,
          request.user,
          janusData
        )
      )
    }
  }

  def provisionedRoleStatus: Action[AnyContent] = authAction {
    implicit request =>
      Ok(
        views.html.provisionedRoleStatus(
          provisionedRoleStatusManager.getCacheStatus,
          request.user,
          janusData
        )
      )
  }

}
