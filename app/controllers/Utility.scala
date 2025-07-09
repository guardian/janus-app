package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import conf.Config
import logic.Owners
import play.api.mvc.*
import play.api.{Configuration, Logging, Mode}

class Utility(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    configuration: Configuration
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

  def gone: Action[AnyContent] = authAction { implicit request =>
    logger.warn(
      s"410 response served for request '${request.method} ${request.path}' from user ${request.user.username}"
    )
    Gone(views.html.gone(request.user, janusData))
  }
}
