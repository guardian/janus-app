package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import com.webauthn4j.util.Base64UrlUtil
import io.circe.Json
import io.circe.syntax.EncoderOps
import logic.Passkey
import models.Passkey._
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
      NoContent.withSession(request.session - challengeAttribName)
    }

    result.merge
  }
}
