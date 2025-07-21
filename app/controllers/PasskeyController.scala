package controllers

import aws.PasskeyChallengeDB.UserChallenge
import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.janus.model.JanusData
import com.webauthn4j.data.client.challenge.{Challenge, DefaultChallenge}
import controllers.Validation.formattedErrors
import logic.Passkey
import logic.UserAccess.hasAccess
import models.JanusException
import models.PasskeyFlow.{Authentication, Registration}
import play.api.data.Form
import play.api.data.Forms.{mapping, text}
import play.api.data.validation.Constraints.*
import play.api.libs.json.Json
import play.api.mvc.*
import play.api.{Logging, Mode}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.util.{Failure, Success, Try}

/** Controller for handling passkey registration and authentication. */
class PasskeyController(
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    passkeyAuthAction: ActionBuilder[UserIdentityRequest, AnyContent],
    passkeyRegistrationAuthAction: ActionBuilder[
      UserIdentityRequest,
      AnyContent
    ],
    host: String,
    janusData: JanusData
)(using dynamoDb: DynamoDbClient, mode: Mode)
    extends AbstractController(controllerComponents)
    with ResultHandler
    with Logging {

  private val appName = mode match {
    case Mode.Dev  => "Janus-Dev"
    case Mode.Test => "Janus-Test"
    case Mode.Prod => "Janus-Prod"
  }

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#generating-a-webauthn-credential-key-pair]].
    */
  def registrationOptions: Action[Unit] = authAction(parse.empty) { request =>
    apiResponse(
      for {
        _ <- verifyHasAccess(request.user)
        loadCredentialsResponse <- PasskeyDB.loadCredentials(request.user)
        options <- Passkey.registrationOptions(
          appName,
          appHost = host,
          user = request.user,
          challenge = new DefaultChallenge(),
          existingPasskeys = PasskeyDB.extractMetadata(loadCredentialsResponse)
        )
        _ <- PasskeyChallengeDB.insert(
          UserChallenge(request.user, Registration, options.getChallenge)
        )
        _ = logger.info(
          s"Created registration options for user ${request.user.username}"
        )
      } yield options
    )
  }

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#registering-the-webauthn-public-key-credential-on-the-server]].
    */
  def register: Action[AnyContent] = passkeyRegistrationAuthAction {
    implicit request =>
      registrationForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Redirect(routes.Janus.userAccount)
              .flashing("error" -> formattedErrors(formWithErrors)),
          registrationData =>
            redirectResponse(routes.Janus.userAccount) {
              for {
                _ <- verifyHasAccess(request.user)
                _ <- validateUniquePasskeyName(
                  request.user,
                  registrationData.passkeyName
                )
                _ <- performPasskeyRegistration(
                  request.user,
                  registrationData
                )
              } yield "Passkey was registered successfully"
            }
        )
  }

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#generating-a-webauthn-assertion]].
    */
  def authenticationOptions: Action[Unit] = authAction(parse.empty) { request =>
    apiResponse(
      for {
        loadCredentialsResponse <- PasskeyDB.loadCredentials(request.user)
        existingPasskeys <-
          if !loadCredentialsResponse.items.isEmpty then
            Success(PasskeyDB.extractMetadata(loadCredentialsResponse))
          else Failure(JanusException.noPasskeysRegistered(request.user))
        options <- Passkey.authenticationOptions(
          appHost = host,
          user = request.user,
          challenge = new DefaultChallenge(),
          existingPasskeys = existingPasskeys
        )
        _ <- PasskeyChallengeDB.insert(
          UserChallenge(request.user, Authentication, options.getChallenge)
        )
        _ = logger.info(
          s"Created authentication options for user ${request.user.username}"
        )
      } yield options
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
  def registrationAuthenticationOptions: Action[Unit] =
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
  def deletePasskey(passkeyId: String): Action[AnyContent] =
    passkeyAuthAction { implicit request =>
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

  /*
   * Validation rules for input fields to the 'register' route.
   * These should correspond with the frontend rules defined in [[passkeys.js#getPasskeysFromUser]].
   */
  private val registrationForm: Form[RegistrationData] = Form(
    mapping(
      "passkey" -> text.verifying(nonEmpty),
      "passkeyName" -> text
        .transform(_.trim, identity[String])
        .verifying(nonEmpty)
        .verifying(maxLength(50))
        .verifying(pattern("^[a-zA-Z0-9 _-]+$".r))
    )(
      RegistrationData.apply
    )(data => Some((data.passkey, data.passkeyName)))
  )

  private def validateUniquePasskeyName(
      user: UserIdentity,
      passkeyName: String
  ): Try[Unit] =
    PasskeyDB.loadCredentials(user).flatMap { credentialsResponse =>
      val passkeys = PasskeyDB.extractMetadata(credentialsResponse)
      if passkeys.exists(_.name.equalsIgnoreCase(passkeyName)) then
        Failure(
          JanusException.duplicatePasskeyNameFieldInRequest(user, passkeyName)
        )
      else Success(())
    }

  private def verifyHasAccess(
      user: UserIdentity
  ): Try[Unit] =
    if hasAccess(user.username, janusData.access) ||
      hasAccess(user.username, janusData.admin)
    then Success(())
    else Failure(JanusException.noAccessFailure(user))

  private def loadRegistrationChallenge(
      user: UserIdentity
  ): Try[Challenge] =
    for {
      challengeResponse <- PasskeyChallengeDB.loadChallenge(user, Registration)
      challenge <- PasskeyChallengeDB.extractChallenge(challengeResponse, user)
    } yield challenge

  private def performPasskeyRegistration(
      user: UserIdentity,
      registrationData: RegistrationData
  ): Try[Unit] =
    for {
      challenge <- loadRegistrationChallenge(user)
      credRecord <- Passkey.verifiedRegistration(
        host,
        user,
        challenge,
        registrationData.passkey
      )
      _ <- PasskeyDB.insert(user, credRecord, registrationData.passkeyName)
      _ <- PasskeyChallengeDB.delete(user, Registration)
      _ = logger.info(s"Registered passkey for user ${user.username}")
    } yield ()

  /** Redirects to a specified path and flashes messages on error. */
  private def redirectResponse(call: Call)(action: => Try[String]): Result =
    action match {
      case Failure(err: JanusException) =>
        logger.error(err.engineerMessage, err.causedBy.orNull)
        Redirect(call).flashing("error" -> err.userMessage)
      case Failure(err) =>
        logger.error(err.getMessage, err)
        Redirect(call).flashing("error" -> "An unexpected error occurred")
      case Success(msg) =>
        Redirect(call).flashing("success" -> msg)
    }
}

case class RegistrationData(passkey: String, passkeyName: String)
