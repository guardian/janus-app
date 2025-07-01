package controllers

import aws.PasskeyChallengeDB.UserChallenge
import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.janus.model.JanusData
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.client.challenge.DefaultChallenge
import controllers.Validation.formattedErrors
import logic.AccountOrdering.orderedAccountAccess
import logic.UserAccess.{userAccess, username}
import logic.{Date, Favourites, Passkey}
import models.JanusException.throwableWrites
import models.PasskeyFlow.{Authentication, Registration}
import models.{
  JanusException,
  PasskeyAuthenticator,
  PasskeyEncodings,
  PasskeyMetadata
}
import play.api.data.Form
import play.api.data.Forms.{mapping, text}
import play.api.data.validation.Constraints.*
import play.api.http.MimeTypes
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import play.api.mvc.*
import play.api.{Logging, Mode}
import play.twirl.api.Html
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.QueryResponse

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}
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
    janusData: JanusData,
    passkeysEnabled: Boolean,
    enablingCookieName: String,
    authenticators: Map[AAGUID, PasskeyAuthenticator]
)(using dynamoDb: DynamoDbClient, mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging {

  private val appName = mode match {
    case Mode.Dev  => "Janus-Dev"
    case Mode.Test => "Janus-Test"
    case Mode.Prod => "Janus-Prod"
  }

  private def apiResponse[A](action: => Try[A]): Result =
    action match {
      case Failure(err: JanusException) =>
        logger.error(err.engineerMessage, err.causedBy.orNull)
        Status(err.httpCode)(toJson(err))
      case Failure(err) =>
        logger.error(err.getMessage, err)
        Status(INTERNAL_SERVER_ERROR)(toJson(err))
      case Success(result: Result) => result
      case Success(html: Html)     => Ok(html)
      case Success(a) =>
        val json = PasskeyEncodings.mapper.writeValueAsString(a)
        Ok(json).as(MimeTypes.JSON)
    }

  /** Redirects to a specified path and flashes messages on error.
    */
  private def redirectResponseOnError[A](
      redirectPath: String
  )(action: => Try[A]): Result =
    action match {
      case Failure(err: JanusException) =>
        logger.error(err.engineerMessage, err.causedBy.orNull)
        Redirect(redirectPath)
          .flashing("error" -> err.userMessage)
      case Failure(err) =>
        logger.error(err.getMessage, err)
        Redirect(redirectPath)
          .flashing(
            "error" -> "An unexpected error occurred"
          )
      case Success(result: Result) => result
      case Success(html: Html)     => Ok(html)
      case Success(a) =>
        val json = PasskeyEncodings.mapper.writeValueAsString(a)
        Ok(json).as(MimeTypes.JSON)
    }

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#generating-a-webauthn-credential-key-pair]].
    */
  def registrationOptions: Action[Unit] = authAction(parse.empty) { request =>
    apiResponse(
      for {
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

  /*
   * Validation rules for input fields to the 'register' route.
   * These should correspond with the frontend rules defined in [[passkeys.js#getPasskeysFromUser]].
   */
  private val registrationForm: Form[RegistrationData] = Form(
    mapping(
      "passkey" -> text.verifying(nonEmpty),
      "passkeyName" -> text
        .transform(_.trim, identity)
        .verifying(nonEmpty)
        .verifying(maxLength(50))
        .verifying(pattern("^[a-zA-Z0-9 _-]+$".r))
    )(
      RegistrationData.apply
    )(data => Some((data.passkey, data.passkeyName)))
  )

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#registering-the-webauthn-public-key-credential-on-the-server]].
    */
  def register: Action[AnyContent] = passkeyRegistrationAuthAction {
    implicit request =>
      registrationForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Redirect("/user-account")
              .flashing("error" -> formattedErrors(formWithErrors)),
          registrationData =>
            redirectResponseOnError("/user-account")(for {
              _ <- validateRegistrationData(request.user, registrationData)
              result <- performPasskeyRegistration(
                request.user,
                registrationData
              )
            } yield result)
        )
  }

  /** Validates the registration data against database content. */
  private def validateRegistrationData(
      user: UserIdentity,
      registrationData: RegistrationData
  ): Try[Unit] =
    PasskeyDB.loadCredentials(user).flatMap { credentialsResponse =>
      val passkeys = PasskeyDB.extractMetadata(credentialsResponse)
      if passkeys.exists(_.name.equalsIgnoreCase(registrationData.passkeyName))
      then
        Failure(
          JanusException.duplicatePasskeyNameFieldInRequest(
            user,
            registrationData.passkeyName
          )
        )
      else Success(())
    }

  /** Performs the core passkey registration process including cryptographic
    * verification and database persistence. This method assumes all validation
    * has already been completed.
    */
  private def performPasskeyRegistration(
      user: UserIdentity,
      registrationData: RegistrationData
  ): Try[Result] =
    for {
      challengeResponse <- PasskeyChallengeDB.loadChallenge(user, Registration)
      challenge <- PasskeyChallengeDB.extractChallenge(challengeResponse, user)
      credRecord <- Passkey.verifiedRegistration(
        host,
        user,
        challenge,
        registrationData.passkey
      )
      _ <- PasskeyDB.insert(user, credRecord, registrationData.passkeyName)
      _ <- PasskeyChallengeDB.delete(user, Registration)
      _ = logger.info(s"Registered passkey for user ${user.username}")
    } yield {
      Redirect("/user-account")
        .flashing("success" -> "Passkey was registered successfully")
    }

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#generating-a-webauthn-assertion]].
    */
  def authenticationOptions: Action[Unit] = authAction(parse.empty) { request =>
    apiResponse(
      for {
        loadCredentialsResponse <- PasskeyDB.loadCredentials(request.user)
        existingPasskeys <- loadExistingPasskeysOrFail(
          loadCredentialsResponse,
          request.user
        )
        options <- Passkey.authenticationOptions(
          appHost = host,
          user = request.user,
          challenge = new DefaultChallenge(),
          existingPasskeys
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
          val message = s"Passkey '$passkeyName' was successfully deleted"

          // Return JSON response with flash session info for the toast
          Ok(
            Json.obj(
              "success" -> true,
              "message" -> message,
              "redirect" -> "/user-account"
            )
          )
        }
      )
    }

  // To be removed when passkeyAuthAction has been applied to real endpoints
  def protectedCredentialsPage: Action[AnyContent] = passkeyAuthAction { _ =>
    Ok("This is the protected page you're authorised to see.")
  }

  // To be removed when passkeyAuthAction has been applied to real endpoints
  def pretendAwsConsole: Action[AnyContent] = Action {
    Ok("This is the pretend AWS console.")
  }

  // To be removed when passkeyAuthAction has been applied to real endpoints
  def protectedRedirect: Action[AnyContent] = passkeyAuthAction { _ =>
    Redirect("/passkey/pretend-aws-console")
  }

  // To be removed when passkeyAuthAction has been applied to real endpoints
  def mockHome: Action[AnyContent] = authAction { implicit request =>
    val displayMode =
      Date.displayMode(ZonedDateTime.now(ZoneId.of("Europe/London")))
    (for {
      permissions <- userAccess(username(request.user), janusData.access)
      favourites = Favourites.fromCookie(request.cookies.get("favourites"))
      awsAccountAccess = orderedAccountAccess(permissions, favourites)
      enablingCookieIsPresent = request.cookies
        .get(enablingCookieName)
        .isDefined
    } yield {
      Ok(
        views.html.passkeymock.index(
          awsAccountAccess,
          request.user,
          janusData,
          displayMode,
          passkeysEnabled && enablingCookieIsPresent
        )
      )
    }).getOrElse(Ok(views.html.noPermissions(request.user, janusData)))
  }

  // TODO: move to Janus or account controller
  def showUserAccountPage: Action[AnyContent] = authAction { implicit request =>
    apiResponse {
      def dateTimeFormat(instant: Instant, formatter: DateTimeFormatter) =
        instant.atZone(ZoneId.of("Europe/London")).format(formatter)
      def dateFormat(instant: Instant) =
        dateTimeFormat(instant, DateTimeFormatter.ofPattern("d MMM yyyy"))
      def timeFormat(instant: Instant) =
        dateTimeFormat(
          instant,
          DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss XXXXX")
        )
      for {
        queryResponse <- PasskeyDB.loadCredentials(request.user)
        passkeys = PasskeyDB
          .extractMetadata(queryResponse)
          .map(p => p.copy(authenticator = authenticators.get(p.aaguid)))
      } yield views.html.userAccount(
        request.user,
        janusData,
        passkeys,
        dateFormat,
        timeFormat
      )
    }
  }

  private def loadExistingPasskeysOrFail(
      dbResponse: QueryResponse,
      user: UserIdentity
  ): Try[Seq[PasskeyMetadata]] =
    if !dbResponse.items.isEmpty then
      Success(PasskeyDB.extractMetadata(dbResponse))
    else Failure(JanusException.noPasskeysRegistered(user))
}

case class RegistrationData(passkey: String, passkeyName: String)
