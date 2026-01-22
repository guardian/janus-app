package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.{AwsAccount, JanusData}
import conf.Config
import logic.Accounts
import logic.Accounts.accountPermissions
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

  def rolesStatusForAccount(authConfigKey: String): Action[AnyContent] =
    authAction { implicit request =>
      janusData.accounts
        .find(_.authConfigKey == authConfigKey)
        .map(_.name) match {
        case Some(name) =>

          val rolesStatuses = provisionedRoleStatusManager.getCacheStatus
          val successfullyCreatedRoles =
            Accounts.successfulRolesForThisAccount(rolesStatuses, authConfigKey)
          val rolesWithErrors =
            Accounts.errorRolesForThisAccount(rolesStatuses, authConfigKey)

          Ok(
            views.html.rolesStatus(
              name,
              successfullyCreatedRoles,
              rolesWithErrors,
              request.user,
              janusData
            )
          )

        case None =>
          NotFound(
            views.html.error("Account not found", Some(request.user), janusData)
          )
      }
    }

  def usersForAccount(authConfigKey: String): Action[AnyContent] = authAction {
    implicit request =>
      janusData.accounts
        .find(_.authConfigKey == authConfigKey)
        .map { awsAccount =>
          (
            awsAccount,
            accountPermissions(awsAccount, janusData.access).map(_.userName)
          )
        } match {
        case Some((AwsAccount(name, _), users)) =>
          Ok(
            views.html.users(
              name,
              users,
              request.user,
              janusData
            )
          )
        case None =>
          NotFound(
            views.html.error("Account not found", Some(request.user), janusData)
          )
      }

  }

  def accountInfo(authConfigKey: String): Action[AnyContent] = authAction {
    implicit request =>
      janusData.accounts
        .find(_.authConfigKey == authConfigKey)
        .map(_.name) match {
        case Some(name) =>
          Ok(
            views.html.accountInfo(
              name,
              authConfigKey,
              request.user,
              janusData
            )
          )
        case None =>
          NotFound(
            views.html.error("Account not found", Some(request.user), janusData)
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
