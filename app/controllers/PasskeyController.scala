package controllers

import aws.PasskeyChallengeDB.UserChallenge
import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.janus.model.JanusData
import io.circe.syntax.EncoderOps
import logic.Passkey
import models.JanusException
import models.Passkey._
import play.api.mvc._
import play.api.{Logging, Mode}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.util.control.NonFatal

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
    (for {
      options <- Passkey.registrationOptions(host, request.user)
      _ <- PasskeyChallengeDB.insert(
        UserChallenge(request.user, options.getChallenge)
      )
    } yield {
      logger.info(
        s"Created registration options for user ${request.user.username}"
      )
      Ok(options.asJson.noSpaces)
    }).fold(handleErrors, identity)
  }

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#registering-the-webauthn-public-key-credential-on-the-server]].
    */
  def register: Action[AnyContent] = authAction { request =>
    (for {
      challenge <- PasskeyChallengeDB.load(request.user)
      body <- extractRequestBody(request.body.asText, request.user)
      credRecord <- Passkey.verifiedRegistration(host, challenge, body)
      _ <- PasskeyDB.insert(request.user, credRecord)
      _ <- PasskeyChallengeDB.delete(request.user)
    } yield {
      logger.info(
        s"Registered passkey for user ${request.user.username}"
      )
      NoContent
    }).fold(handleErrors, identity)
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

  private def handleErrors(exception: Throwable): Result =
    exception match {
      case exception: JanusException =>
        logger.error(exception.engineerMessage, exception.causedBy.get)
        Status(exception.httpCode)(exception.userMessage)
      case NonFatal(exception) =>
        logger.error(exception.getMessage, exception)
        Status(INTERNAL_SERVER_ERROR)
    }
}
