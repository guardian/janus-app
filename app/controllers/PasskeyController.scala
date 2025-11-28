package controllers

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.janus.model.JanusData
import logic.UserAccess.hasAccess
import models.JanusException
import play.api.libs.json.Json
import play.api.mvc.*
import play.api.{Logging, Mode}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/** Controller for handling niche Janus-specific passkey operations. */
class PasskeyController(
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    basePasskeyController: com.gu.playpasskeyauth.controllers.BasePasskeyController,
    janusData: JanusData,
    passkeysEnabled: Boolean,
    enablingCookieName: String
)(using mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with ResultHandler
    with Logging {

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

  /** Creates authentication options during the passkey registration flow.
    *
    * This method first verifies the user has access, then generates challenge
    * data.
    *
    * @return
    *   Authentication options containing credentials and challenge data
    */
  def preRegistrationAuthenticationOptions: Action[Unit] = {
    Action.async(parse.empty) { request =>
      authAction.invokeBlock(
        request,
        { (userRequest: UserIdentityRequest[Unit]) =>
          verifyHasAccess(userRequest.user) match {
            case Success(_) =>
              // Delegate to the library's authenticationOptions action
              basePasskeyController.authenticationOptions()(userRequest)
            case Failure(e) =>
              Future.successful(
                Forbidden(Json.obj("error" -> e.getMessage))
              )
          }
        }
      )
    }
  }

  def delete(passkeyId: String) = TODO

  private def verifyHasAccess(
      user: UserIdentity
  ): Try[Unit] =
    if hasAccess(user.username, janusData.access) ||
      hasAccess(user.username, janusData.admin)
    then Success(())
    else Failure(JanusException.noAccessFailure(user))
}
