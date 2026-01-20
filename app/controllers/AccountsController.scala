package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.{AwsAccount, JanusData}
import conf.Config
import logic.Accounts
import models.AwsAccountIamRoleInfoStatus
import play.api.mvc.*
import play.api.{Configuration, Logging, Mode}
import services.ProvisionedRoleStatusManager

import scala.util.Try

class AccountsController(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    configuration: Configuration,
    provisionedRoleStatusManager: ProvisionedRoleStatusManager
)(using mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging
    with Accounts {

  def lookupAccountId: AwsAccount => Try[String] = account =>
    Config.accountNumber(account.authConfigKey, configuration)

  def accounts: Action[AnyContent] = authAction { implicit request =>
    val accountData = accountOwnerInformation(
      janusData.accounts,
      janusData.access
    )(lookupAccountId, lookupAccountRoles)

    // log any account number errors we accumulated
    accountIdErrors(accountData)
      .foreach { case (account, err) =>
        logger
          .warn(
            s"Couldn't lookup account number for ${account.authConfigKey}",
            err
          )
      }
    Ok(views.html.accounts(accountData, request.user, janusData))
  }

  def rolesStatuses: Map[AwsAccount, AwsAccountIamRoleInfoStatus] =
    provisionedRoleStatusManager.getCacheStatus

  def accountRoles: Action[AnyContent] = authAction { implicit request =>
    Ok(
      views.html
        .accountRoles(
          getAccountRoles,
          getFailedAccountRoles,
          request.user,
          janusData
        )
    )
  }

  private def accountOwnersLookup(account: String) = accountOwnerInformation(
    janusData.accounts,
    janusData.access
  )(lookupAccountId, lookupAccountRoles)
    .find(_.account.authConfigKey == account)
    .map(_.permissions.map(_.userName))
    .getOrElse(List.empty)

  def rolesStatusForAccount(authConfigKey: String): Action[AnyContent] = {

    val accountName =
      janusData.accounts.find(_.authConfigKey == authConfigKey).map(_.name)

    authAction { implicit request =>
      Ok(
        views.html.rolesStatus(
          accountName.getOrElse("Unknown Account"),
          successfulRolesForThisAccount(authConfigKey),
          errorRolesForThisAccount(authConfigKey),
          request.user,
          janusData
        )
      )
    }
  }

  def usersForAccount(authConfigKey: String): Action[AnyContent] = {

    authAction { implicit request =>
      Ok(
        views.html.users(
          authConfigKey,
          accountOwnersLookup(authConfigKey),
          request.user,
          janusData
        )
      )
    }
  }

  def accountInfo(authConfigKey: String): Action[AnyContent] = {
    val accountName =
      janusData.accounts.find(_.authConfigKey == authConfigKey).map(_.name)

    val accountUsers: List[String] = accountOwnerInformation(
      janusData.accounts,
      janusData.access
    )(lookupAccountId, lookupAccountRoles)
      .find(accountInfo => accountInfo.account.authConfigKey == authConfigKey)
      .map(_.permissions.map(_.userName))
      .getOrElse(Nil)

    authAction { implicit request =>
      Ok(
        views.html.accountInfo(
          accountName.getOrElse("Unknown account"),
          authConfigKey,
          accountUsers,
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
