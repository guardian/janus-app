package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import conf.Config
import logic.Owners
import play.api.mvc.*
import play.api.{Configuration, Logging, Mode}

import java.time.Duration

class Utility(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    configuration: Configuration,
    passkeysEnablingCookieName: String
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

  /** Test endpoint for passkey autofill behavior - INSECURE, for testing only
    */
  def testPasskeyAutofill: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.testPasskeyAutofill())
  }

  /** Test endpoint for button-triggered passkey behavior - INSECURE, for
    * testing only
    */
  def testPasskeyButton: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.testPasskeyButton())
  }

  /** Test endpoint for passkey creation on page load - INSECURE, for testing
    * only
    */
  def testPasskeyCreate: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.testPasskeyCreate())
  }
}
