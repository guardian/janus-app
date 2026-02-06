package controllers

import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.janus.model.JanusData
import com.gu.playpasskeyauth.PasskeyAuth
import com.gu.playpasskeyauth.models.UserId
import logic.UserAccess.hasAccess
import models.JanusException
import play.api.mvc.*
import play.api.{Logging, Mode}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

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

  /** Creates authentication options during the passkey registration flow.
    *
    * This method generates the necessary challenge data for authenticating a
    * user who already has passkeys registered before they can register a new
    * passkey. It loads the user's existing credentials, creates authentication
    * options with a new challenge, and stores this challenge in the database
    * for future verification.
    *
    * @return
    *   Authentication options containing credentials and challenge data
    */
  def preRegistrationAuthenticationOptions: Action[Unit] =
    authAction(parse.empty).async { request =>
      (for {
        _ <- Future.successful(verifyHasAccess(request.user))
        options <- passkeyAuth.verificationService.buildAuthenticationOptions(
          UserId(request.user.username)
        )
        _ = logger.info(
          s"Created registration authentication options for user ${request.user.username}"
        )
      } yield apiResponse(Success(options))).recover { case err =>
        apiResponse(Failure(err))
      }
    }

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

  private def verifyHasAccess(
      user: UserIdentity
  ): Try[Unit] =
    if hasAccess(user.username, janusData.access) ||
      hasAccess(user.username, janusData.admin)
    then Success(())
    else Failure(JanusException.noAccessFailure(user))
}
