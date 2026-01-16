package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.{AwsAccount, JanusData}
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

  private val lookupAccountId: AwsAccount => Try[String] = account =>
    Config.accountNumber(account.authConfigKey, configuration)

  private val lookupAccountRoles
      : (AwsAccount, Try[String]) => Set[IamRoleInfo] =
    (account, accountIdMaybe) =>
      accountIdMaybe match {
        case Success(accountId) =>
          rolesStatuses.filter(_.roleArn.accountId().toScala contains accountId)
        case _ => Set.empty
      }

  def accounts: Action[AnyContent] = authAction { implicit request =>

    val accountData = Owners.accountOwnerInformation(
      janusData.accounts,
      janusData.access
    )(lookupAccountId, lookupAccountRoles)

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
          request.user,
          janusData
        )
      )
  }

  def accountRoles: Action[AnyContent] = authAction { implicit request =>
    val roles: Map[String, (Set[String], Set[String])] =
      rolesStatuses.groupBy(_.roleName).map { (k, v) =>
        val accounts: Set[String] =
          v.flatMap(r => r.roleArn.accountId().toScala)
        val tagNames: Set[String] = v.flatMap(_.tags.keys)
        (k, (accounts, tagNames))
      }
    Ok(
      views.html
        .accountRoles(
          roles,
          request.user,
          janusData
        )
    )
  }

  private def accountOwnersLookup(account: String) = Owners
    .accountOwnerInformation(
      janusData.accounts,
      janusData.access
    )(lookupAccountId, lookupAccountRoles)
    .find(_.account.name == account)

  def rolesStatusForAccount(account: String): Action[AnyContent] = {

    val rolesForThisAccount =
      accountOwnersLookup(account).map(_.configuredRole) match {
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

  def usersForAccount(account: String): Action[AnyContent] = {

    val usersForThisAccount: List[String] =
      accountOwnersLookup(account).map(_.permissions.map(_.userName)) match {
        case Some(users) => users
        case _           => List.empty
      }

    authAction { implicit request =>
      Ok(
        views.html.users(
          account,
          usersForThisAccount,
          request.user,
          janusData
        )
      )
    }
  }

  def moreInfo(account: String): Action[AnyContent] = {
    val accountKey = accountOwnersLookup(account).map(_.account.authConfigKey)

    val accountUsers: List[String] = Owners
      .accountOwnerInformation(
        janusData.accounts,
        janusData.access
      )(lookupAccountId, lookupAccountRoles)
      .find(accountInfo =>
        accountKey.contains(
          accountInfo.account.authConfigKey
        )
      )
      .map(_.permissions.map(_.userName))
      .getOrElse(Nil)

    authAction { implicit request =>
      Ok(
        views.html.moreInfo(
          account,
          accountKey,
          accountUsers,
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
