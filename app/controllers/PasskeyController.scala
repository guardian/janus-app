package controllers

import aws.PasskeyChallengeDB.UserChallenge
import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.janus.model.JanusData
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json}
import logic.Passkey
import models.JanusException
import models.Passkey._
import play.api.mvc._
import play.api.{Logging, Mode}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

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
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.data._
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.{Challenge, DefaultChallenge}
import com.webauthn4j.server.ServerProperty
import play.api.libs.json.{JsNumber, JsString, Json, Writes}
import play.api.mvc._
import play.api.{Logging, Mode}

/** Controller for handling passkey registration and authentication. */
class PasskeyController(
                         controllerComponents: ControllerComponents,
                         authAction: AuthAction[AnyContent],
                         host: String,
                         janusData: JanusData
                       )(implicit mode: Mode, assetsFinder: AssetsFinder)
  extends AbstractController(controllerComponents)
    with Logging {

  private val appName = "Janus"

  def showRegistrationPage: Action[AnyContent] = authAction {
    implicit request =>
      Ok(views.html.passkeyRegistration(request.user, janusData))
  }

  // TODO persist instead of using session - store with TTL
  private val challengeAttribName = "passkeyChallenge"

  def registrationOptions: Action[AnyContent] = authAction { request =>
    Passkey.registrationOptions(appName, host, request.user) match {

      case Left(failure) =>
        logger.error(
          s"Failed to create registration options for user ${request.user.username}: ${failure.details}",
          failure.cause
        )
        BadRequest("Failed to create registration options")

      case Right(options) =>
        val challenge =
          Base64UrlUtil.encodeToString(options.getChallenge.getValue)
        logger.info(
          s"Created registration options for user ${request.user.username}"
        )
        Ok(options.asJson.noSpaces)
          .withSession(request.session + (challengeAttribName -> challenge))
    }
  }

  def register: Action[AnyContent] = authAction { request =>
    val result = for {
      challenge <- request.session.get(challengeAttribName).toRight {
        logger.error(
          s"Missing challenge in session for user ${request.user.username}"
        )
        BadRequest("Registration session invalid")
      }

      body <- request.body.asText.toRight {
        logger.error(
          s"Missing body in registration request for user ${request.user.username}"
        )
        BadRequest("Invalid request format")
      }

      credRecord <- Passkey
        .verifiedRegistration(host, challenge, body)
        .left
        .map { failure =>
          logger.error(
            s"Registration verification failed for user ${request.user.username}: ${failure.details}",
            failure.cause
          )
          BadRequest("Registration verification failed")
        }

      _ <- Passkey.store(request.user, credRecord).left.map { failure =>
        logger.error(
          s"Failed to store credential for user ${request.user.username}: ${failure.details}",
          failure.cause
        )
        InternalServerError("Failed to store credential")
      }
    } yield {
      logger.info(
        s"Successfully registered passkey for user ${request.user.username}"
      )
      Ok(Json.obj("success" -> true.asJson).noSpaces)
        .withSession(request.session - challengeAttribName)
    }

    result.merge
  }
}
