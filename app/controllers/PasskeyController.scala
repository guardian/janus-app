package controllers

import aws.PasskeyChallengeDB.UserChallenge
import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.janus.model.JanusData
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json}
import logic.AccountOrdering.orderedAccountAccess
import logic.UserAccess.{userAccess, username}
import logic.{Date, Favourites, Passkey}
import models.JanusException
import models.Passkey._
import play.api.mvc.{Action, _}
import play.api.{Logging, Mode}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import java.time.{ZoneId, ZonedDateTime}
import scala.util.{Failure, Success, Try}

/** Controller for handling passkey registration and authentication. */
class PasskeyController(
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    host: String,
    janusData: JanusData
)(implicit dynamoDb: DynamoDbClient, mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging {

  private val appName = mode match {
    case Mode.Dev  => "Janus-Dev"
    case Mode.Test => "Janus-Test"
    case Mode.Prod => "Janus-Prod"
  }

  private def apiResponse[A](
      action: => Try[A]
  )(implicit encoder: Encoder[A]): Result =
    action match {
      case Failure(err: JanusException) =>
        logger.error(err.engineerMessage, err.causedBy.orNull)
        val json = Json.obj(
          "status" -> Json.fromString("error"),
          "message" -> Json.fromString(err.userMessage)
        )
        Status(err.httpCode)(json.noSpaces)
      case Failure(err) =>
        logger.error(err.getMessage, err)
        val json = Json.obj(
          "status" -> Json.fromString("error"),
          "message" -> Json.fromString(err.getClass.getSimpleName)
        )
        Status(INTERNAL_SERVER_ERROR)(json.noSpaces)
      case Success(a) => Ok(a.asJson.noSpaces)
    }

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#generating-a-webauthn-credential-key-pair]].
    */
  def registrationOptions: Action[AnyContent] = authAction { request =>
    apiResponse(
      for {
        options <- Passkey.registrationOptions(appName, host, request.user)
        _ <- PasskeyChallengeDB.insert(
          UserChallenge(request.user, options.getChallenge)
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
  def register: Action[AnyContent] = authAction { request =>
    apiResponse(
      for {
        challengeResponse <- PasskeyChallengeDB.loadChallenge(request.user)
        challenge <- PasskeyChallengeDB.extractChallenge(
          challengeResponse,
          request.user
        )
        body <- extractRequestBody(request.body.asText, request.user)
        credRecord <- Passkey.verifiedRegistration(host, challenge, body)
        _ <- PasskeyDB.insert(request.user, credRecord)
        _ <- PasskeyChallengeDB.delete(request.user)
        _ = logger.info(s"Registered passkey for user ${request.user.username}")
      } yield ()
    )
  }

  private def extractRequestBody(body: Option[String], user: UserIdentity) =
    body
      .toRight(
        JanusException(
          userMessage = "Missing body in registration request",
          engineerMessage =
            s"Missing body in registration request for user ${user.username}",
          httpCode = BAD_REQUEST,
          causedBy = None
        )
      )
      .toTry

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#generating-a-webauthn-assertion]].
    */
  def authenticationOptions: Action[AnyContent] = authAction { request =>
    apiResponse(
      for {
        options <- Passkey.authenticationOptions(request.user)
        _ <- PasskeyChallengeDB.insert(
          UserChallenge(request.user, options.getChallenge)
        )
        _ = logger.info(
          s"Created authentication options for user ${request.user.username}"
        )
      } yield options
    )
  }

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#webauthn-assertion-verification-and-post-processing]].
    */
  def authenticate: Action[AnyContent] = authAction { request =>
    apiResponse(
      for {
        challengeResponse <- PasskeyChallengeDB.loadChallenge(request.user)
        challenge <- PasskeyChallengeDB.extractChallenge(
          challengeResponse,
          request.user
        )
        authData <- extractAuthenticationData(request)
        credentialResponse <- PasskeyDB.loadCredential(
          request.user,
          authData.getCredentialId
        )
        credential <- PasskeyDB.extractCredential(
          credentialResponse,
          request.user
        )
        verifiedAuthData <- Passkey.verifiedAuthentication(
          host,
          challenge,
          authData,
          credential
        )
        _ <- PasskeyChallengeDB.delete(request.user)
        _ <- PasskeyDB.updateCounter(request.user, verifiedAuthData)
        _ = logger.info(
          s"Authenticated passkey for user ${request.user.username}"
        )
      } yield ()
    )
  }

  private def extractAuthenticationData(
      request: UserIdentityRequest[AnyContent]
  ) =
    for {
      body <- request.body.asText
        .toRight(
          JanusException(
            userMessage = "Missing body in authentication request",
            engineerMessage =
              s"Missing body in authentication request for user ${request.user.username}",
            httpCode = BAD_REQUEST,
            causedBy = None
          )
        )
        .toTry
      authData <- Passkey.parsedAuthentication(body)
    } yield authData

  def passkeyProtectedTest = TODO

  def mockHome = authAction { implicit request =>
    val displayMode =
      Date.displayMode(ZonedDateTime.now(ZoneId.of("Europe/London")))
    (for {
      permissions <- userAccess(username(request.user), janusData.access)
      favourites = Favourites.fromCookie(request.cookies.get("favourites"))
      awsAccountAccess = orderedAccountAccess(permissions, favourites)
    } yield {
      Ok(
        views.html.passkeysMockIndex(awsAccountAccess, request.user, janusData, displayMode)
      )
    }) getOrElse Ok(views.html.noPermissions(request.user, janusData))
  }

  def showUserAccountPage: Action[AnyContent] = authAction { implicit request =>
    Ok(views.html.userAccount(request.user, janusData))
  }
}
