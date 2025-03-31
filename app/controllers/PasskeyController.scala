package controllers

import aws.PasskeyChallengeDB.UserChallenge
import aws.PasskeyDB.UserCredentialRecord
import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import com.webauthn4j.util.Base64UrlUtil
import io.circe.syntax.EncoderOps
import logic.Passkey
import models.Passkey._
import play.api.mvc._
import play.api.{Logging, Mode}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.util.{Failure, Success}

/** Controller for handling passkey registration and authentication. */
class PasskeyController(
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    host: String,
    janusData: JanusData
)(implicit dynamoDb: DynamoDbClient, mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging {

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#generating-a-webauthn-credential-key-pair]].
    */
  def registrationOptions: Action[AnyContent] = authAction { request =>
    val result = for {
      options <- Passkey
        .registrationOptions(host, request.user)
        .toEither
        .left
        .map { exception =>
          logger.error(
            s"Failed to create registration options for user ${request.user.username}: ${exception.getMessage}",
            exception
          )
          BadRequest("Failed to create registration options")
        }

      _ <- PasskeyChallengeDB
        .insert(UserChallenge(request.user, options.getChallenge))
        .toEither
        .left
        .map { exception =>
          logger.error(
            s"Failed to store challenge for user ${request.user.username}: ${exception.getMessage}",
            exception
          )
          InternalServerError("Failed to store challenge")
        }
    } yield {
      logger.info(
        s"Created registration options for user ${request.user.username}"
      )
      Ok(options.asJson.noSpaces)
    }

    result.merge
  }

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#registering-the-webauthn-public-key-credential-on-the-server]].
    */
  def register: Action[AnyContent] = authAction { request =>
    val result = for {
      challenge <- PasskeyChallengeDB.load(request.user) match {
        case Failure(exception) =>
          logger.error(
            s"Failed to load challenge for user ${request.user.username}: ${exception.getMessage}",
            exception
          )
          Left(BadRequest("Registration session invalid"))
        case Success(None) =>
          logger.error(
            s"Challenge not found for user ${request.user.username}"
          )
          Left(BadRequest("Registration session invalid"))
        case Success(Some(challenge)) =>
          Right(Base64UrlUtil.encodeToString(challenge.getValue))
      }

      body <- request.body.asText.toRight {
        logger.error(
          s"Missing body in registration request for user ${request.user.username}"
        )
        BadRequest("Invalid request format")
      }

      credRecord <- Passkey
        .verifiedRegistration(host, challenge, body)
        .toEither
        .left
        .map { exception =>
          logger.error(
            s"Registration verification failed for user ${request.user.username}: ${exception.getMessage}",
            exception
          )
          BadRequest("Registration verification failed")
        }

      _ <- PasskeyChallengeDB.delete(request.user).toEither.left.map {
        exception =>
          logger.error(
            s"Failed to delete challenge for user ${request.user.username}: ${exception.getMessage}",
            exception
          )
          InternalServerError("Failed to delete challenge")
      }

      _ <- PasskeyDB
        .insert(UserCredentialRecord(request.user, credRecord))
        .toEither
        .left
        .map { exception =>
          logger.error(
            s"Failed to store credential for user ${request.user.username}: ${exception.getMessage}",
            exception
          )
          InternalServerError("Failed to store credential")
        }
    } yield {
      logger.info(
        s"Successfully registered passkey for user ${request.user.username}"
      )
      NoContent
    }

    result.merge
  }
}
