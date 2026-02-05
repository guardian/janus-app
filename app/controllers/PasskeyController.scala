package controllers

import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.janus.model.JanusData
import com.gu.playpasskeyauth.PasskeyAuth
import play.api.mvc.*
import play.api.{Logging, Mode}

import scala.concurrent.ExecutionContext

/** Controller for handling passkey registration and authentication. */
class PasskeyController(
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    passkeyAuth: PasskeyAuth[UserIdentity, AnyContent],
    janusData: JanusData,
    passkeysEnabled: Boolean,
    enablingCookieName: String
)(using Mode, AssetsFinder, ExecutionContext)
    extends AbstractController(controllerComponents)
    with ResultHandler
    with Logging {

  def registrationOptions: Action[Unit] =
    passkeyAuth.controller().creationOptions

  def register: Action[AnyContent] = passkeyAuth.controller().register

  def authenticationOptions: Action[Unit] =
    passkeyAuth.controller().authenticationOptions

  def showAuthPage: Action[AnyContent] = authAction { implicit request =>
    val enablingCookieIsPresent =
      request.cookies.get(enablingCookieName).isDefined
    Ok(
      views.html.passkeyAuth(
        request.user,
        janusData,
        passkeysEnabled && enablingCookieIsPresent
      )
    )
  }

  def deletePasskey(passkeyId: String): Action[Unit] =
    passkeyAuth.controller().delete(passkeyId)
}
