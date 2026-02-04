package controllers

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.janus.model.JanusData
import com.gu.playpasskeyauth.models.{PasskeyId, PasskeyUser, UserId}
import com.gu.playpasskeyauth.services.PasskeyRepository
import filters.ConditionalPasskeyVerificationAction
import logic.UserAccess.hasAccess
import models.JanusException
import play.api.libs.json.Json
import play.api.mvc.*
import play.api.{Logging, Mode}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Controller for handling passkey registration and authentication. */
class PasskeyController(
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    basePasskeyController: com.gu.playpasskeyauth.controllers.PasskeyController[
      UserIdentity,
      AnyContent
    ],
    verificationAction: ConditionalPasskeyVerificationAction,
    janusData: JanusData,
    passkeyDb: PasskeyRepository,
    passkeysEnabled: Boolean,
    enablingCookieName: String
)(using
    dynamoDb: DynamoDbClient,
    mode: Mode,
    assetsFinder: AssetsFinder,
    passkeyUser: PasskeyUser[UserIdentity],
    ec: ExecutionContext
) extends AbstractController(controllerComponents)
    with ResultHandler
    with Logging {

  def registrationOptions: Action[Unit] = basePasskeyController.creationOptions

  def register: Action[AnyContent] = basePasskeyController.register

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

  def authenticationOptions: Action[Unit] =
    basePasskeyController.authenticationOptions

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

  /** Deletes a passkey from the user's account */
  def delete(passkeyId: String): Action[AnyContent] =
    verificationAction { implicit request =>
      val userId = UserId.from(request.user)
      val keyId = PasskeyId.fromBase64Url(passkeyId)
      apiResponse(
        Try(
          Await.result(
            for {
              passkeyInfo <- passkeyDb.loadPasskeyInfo(userId, keyId)
              _ <- passkeyDb.deletePasskey(userId, keyId)
              _ = logger.info(
                s"Deleted passkey for user ${request.user.username} with ID $passkeyId"
              )
            } yield {
              Ok(
                Json.obj(
                  "success" -> true,
                  "message" -> s"Passkey '${passkeyInfo.name}' was successfully deleted",
                  "redirect" -> routes.Janus.userAccount.url
                )
              )
            },
            Duration.Inf
          )
        )
      )
    }

  private def verifyHasAccess(
      user: UserIdentity
  ): Try[Unit] =
    if hasAccess(user.username, janusData.access) ||
      hasAccess(user.username, janusData.admin)
    then Success(())
    else Failure(JanusException.noAccessFailure(user))
}
