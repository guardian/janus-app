package controllers

import com.gu.googleauth.{GoogleAuthConfig, GoogleGroupChecker, LoginSupport}
import com.gu.janus.model.JanusData
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext


class AuthController(janusData: JanusData, controllerComponents: ControllerComponents, val authConfig: GoogleAuthConfig, val googleGroupChecker: GoogleGroupChecker, requiredGoogleGroups: Set[String])
                    (implicit val wsClient: WSClient, ec: ExecutionContext, assetsFinder: AssetsFinder)
  extends AbstractController(controllerComponents) with LoginSupport {

  override val failureRedirectTarget: Call = routes.AuthController.loginError
  override val defaultRedirectTarget: Call = routes.Janus.index

  def login = Action.async { implicit request =>
    startGoogleLogin()
  }

  def loginError = Action { implicit request =>
    val error = request.flash.get("error").getOrElse("There was an error logging in")
    Ok(views.html.error(error, None, janusData))
  }

  def logout = Action { implicit request =>
    Redirect(routes.Janus.index).withNewSession
  }

  def oauthCallback = Action.async { implicit request =>
    processOauth2Callback(requiredGoogleGroups, googleGroupChecker)
  }
}
