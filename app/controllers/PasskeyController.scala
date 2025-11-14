package controllers

import aws.PasskeyChallengeDB.UserChallenge
import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.janus.model.JanusData
import com.gu.playpasskeyauth.web.RequestWithAuthenticationData
import com.webauthn4j.data.client.challenge.DefaultChallenge
import logic.Passkey
import logic.UserAccess.hasAccess
import models.JanusException
import models.PasskeyFlow.Authentication
import play.api.libs.json.Json
import play.api.mvc.*
import play.api.{Logging, Mode}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.util.{Failure, Success, Try}

/** Controller for handling niche Janus-specific passkey operations. */
class PasskeyController(
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    passkeyVerificationAction: ActionBuilder[
      RequestWithAuthenticationData,
      AnyContent
    ],
    passkeyPreRegistrationVerificationAction: ActionBuilder[
      RequestWithAuthenticationData,
      AnyContent
    ],
    host: String,
    janusData: JanusData,
    passkeysEnabled: Boolean,
    enablingCookieName: String
)(using dynamoDb: DynamoDbClient, mode: Mode, assetsFinder: AssetsFinder)
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
    * This method generates the necessary challenge data for authenticating a
    * user who already has passkeys registered before they can register a new
    * passkey. It loads the user's existing credentials, creates authentication
    * options with a new challenge, and stores this challenge in the database
    * for future verification.
    *
    * @return
    *   Authentication options containing credentials and challenge data
    */
  // TODO remove this - required because of verifyHasAccess condition
  def preRegistrationAuthenticationOptions: Action[Unit] =
    authAction(parse.empty) { request =>
      apiResponse(
        for {
          _ <- verifyHasAccess(request.user)
          loadCredentialsResponse <- PasskeyDB.loadCredentials(request.user)
          options <- Passkey.authenticationOptions(
            appHost = host,
            user = request.user,
            challenge = new DefaultChallenge(),
            existingPasskeys =
              PasskeyDB.extractMetadata(loadCredentialsResponse)
          )
          _ <- PasskeyChallengeDB.insert(
            UserChallenge(request.user, Authentication, options.getChallenge)
          )
          _ = logger.info(
            s"Created registration authentication options for user ${request.user.username}"
          )
        } yield options
      )
    }

  /** Deletes a passkey from the user's account */
  // TODO - replace with lib action
  def deletePasskey(passkeyId: String): Action[AnyContent] =
    passkeyVerificationAction { implicit request =>
      apiResponse(
        for {
          // Look up the passkey before deleting to include the name in the success message
          queryResponse <- PasskeyDB.loadCredentials(request.user)
          passkeys = PasskeyDB.extractMetadata(queryResponse)
          passkeyName <- passkeys
            .find(_.id == passkeyId)
            .map(_.name)
            .toRight(
              JanusException.missingItemInDb(request.user, "Passkeys")
            )
            .toTry
          _ <- PasskeyDB.deleteById(request.user, passkeyId)
          _ = logger.info(
            s"Deleted passkey for user ${request.user.username} with ID $passkeyId"
          )
        } yield {
          Ok(
            Json.obj(
              "success" -> true,
              "message" -> s"Passkey '$passkeyName' was successfully deleted",
              "redirect" -> routes.Janus.userAccount.url
            )
          )
        }
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
