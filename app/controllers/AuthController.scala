package controllers

import com.gu.googleauth.{GoogleAuthConfig, GoogleGroupChecker, LoginSupport}
import com.gu.janus.model.JanusData
import play.api.Mode
import play.api.libs.ws.WSClient
import play.api.mvc.*

import scala.concurrent.ExecutionContext

class AuthController(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    val authConfig: GoogleAuthConfig,
    val googleGroupChecker: GoogleGroupChecker,
    requiredGoogleGroups: Set[String]
)(using
    val wsClient: WSClient,
    ec: ExecutionContext,
    mode: Mode,
    assetsFinder: AssetsFinder
) extends AbstractController(controllerComponents)
    with LoginSupport {

  override val failureRedirectTarget: Call = routes.AuthController.loginError
  override val defaultRedirectTarget: Call = routes.Janus.index

  def login: Action[AnyContent] = Action.async { implicit request =>
    startGoogleLogin()
  }

  def loginError: Action[AnyContent] = Action { implicit request =>
    val error =
      request.flash.get("error").getOrElse("There was an error logging in")
    Ok(views.html.error(error, None, janusData))
  }

  def logout: Action[AnyContent] = Action { implicit request =>
    Redirect(routes.Janus.index).withNewSession
  }

  def oauthCallback: Action[AnyContent] = Action.async { implicit request =>
    processOauth2Callback(requiredGoogleGroups, googleGroupChecker)
  }
}
