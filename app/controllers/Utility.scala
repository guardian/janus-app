package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import conf.Config
import logic.Owners
import play.api.mvc.*
import play.api.{Configuration, Logging, Mode}
import services.ProvisionedRoleStatusManager

import java.time.Duration

class Utility(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    configuration: Configuration,
    passkeysEnablingCookieName: String,
    provisionedRoleStatusManager: ProvisionedRoleStatusManager
)(using mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging {

  def healthcheck: Action[AnyContent] = Action {
    Ok("ok")
  }

  def accounts: Action[AnyContent] = authAction { implicit request =>
    val accountData = Owners.accountOwnerInformation(
      janusData.accounts.toList,
      janusData.access
    )(account => Config.accountNumber(account.authConfigKey, configuration))

    // log any account number errors we accumulated
    Owners
      .accountIdErrors(accountData)
      .foreach { case (account, err) =>
        logger
          .warn(s"Couldn't lookup account number for ${account.name}", err)
      }
    Ok(views.html.accounts(accountData, request.user, janusData))
  }

  def provisionedRoleStatus: Action[AnyContent] = authAction {
    implicit request =>
      Ok(
        views.html.provisionedRoleStatus(
          provisionedRoleStatusManager.getCacheStatus,

  def rolesStatus: Action[AnyContent] = authAction { implicit request =>
    Ok(
      views.html
        .rolesStatus("All", rolesStatuses, request.user, janusData)
    )
  }

  private val accountOwnersLookup = Owners
    .accountOwnerInformation(
      janusData.accounts.toList,
      janusData.access
    )(account => Config.accountNumber(account.authConfigKey, configuration))

  def rolesStatusForAccount(account: String): Action[AnyContent] = {
    val matchingAccountMaybe: Option[Try[String]] = accountOwnersLookup
      .find(_._1.name == account)
      .map(_._3)

    val rolesForThisAccount = matchingAccountMaybe match {
      case Some(Success(accountId)) =>
        rolesStatuses.filter(roleStatus =>
          roleStatus.roleArn.accountId().toScala.contains(accountId)
        )
      case _ => Set.empty
    }

    authAction { implicit request =>
      Ok(
        views.html.rolesStatus(
          account,
          rolesForThisAccount,
          request.user,
          janusData
        )
      )
    }
  }

  /** Temporary action to opt in to the passkeys integration */
  def optInToPasskeys: Action[AnyContent] = authAction { _ =>
    Redirect(routes.Janus.userAccount).withCookies(
      Cookie(
        name = passkeysEnablingCookieName,
        value = "true",
        maxAge = Some(Duration.ofDays(30).toSeconds.intValue)
      )
    )
  }

  /** Temporary action to opt out of the passkeys integration */
  def optOutOfPasskeys: Action[AnyContent] = authAction { _ =>
    Redirect(routes.Janus.userAccount)
      .discardingCookies(DiscardingCookie(passkeysEnablingCookieName))
  }
}
