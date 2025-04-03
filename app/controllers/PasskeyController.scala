package controllers

import aws.PasskeyChallengeDB.UserChallenge
import aws.PasskeyDB.UserCredentialRecord
import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.janus.model.JanusData
import com.webauthn4j.data.PublicKeyCredentialCreationOptions
import com.webauthn4j.util.Base64UrlUtil
import io.circe.syntax.EncoderOps
import logic.Passkey
import models.Passkey._
import models.{JanusException, JanusExceptionWithCause, NotFoundException}
import play.api.mvc._
import play.api.{Logging, Mode}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

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
    val outcome = for {
      options <- Passkey.registrationOptions(host, request.user)
      _ <- PasskeyChallengeDB.insert(
        UserChallenge(request.user, options.getChallenge)
      )
    } yield options
    handleErrors(
      outcome,
      successLogMessage =
        s"Created registration options for user ${request.user.username}",
      successResult = (options: PublicKeyCredentialCreationOptions) =>
        Ok(options.asJson.noSpaces)
    )
  }

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#registering-the-webauthn-public-key-credential-on-the-server]].
    */
  def register: Action[AnyContent] = authAction { request =>
    val outcome = for {
      challenge <- PasskeyChallengeDB.load(request.user)
      challengeStr = Base64UrlUtil.encodeToString(challenge.getValue)
      body <- extractRequestBody(request.body.asText, request.user)
      credRecord <- Passkey.verifiedRegistration(host, challengeStr, body)
      _ <- PasskeyDB.insert(UserCredentialRecord(request.user, credRecord))
      _ <- PasskeyChallengeDB.delete(request.user)
    } yield ()
    handleErrors(
      outcome,
      successLogMessage =
        s"Registered passkey for user ${request.user.username}",
      successResult = (_: Unit) => NoContent
    )
  }

  private def extractRequestBody(body: Option[String], user: UserIdentity) =
    body.toRight(
      NotFoundException(
        "Missing body in registration request",
        s"Missing body in registration request for user ${user.username}"
      )
    )

  private def handleErrors[A](
      outcome: Either[JanusException, A],
      successLogMessage: String,
      successResult: A => Result
  ): Result = {
    outcome match {
      case Left(exception: JanusExceptionWithCause) =>
        logger.error(exception.engineerMessage, exception.cause)
        Status(exception.httpCode)(exception.userMessage)
      case Left(exception: JanusException) =>
        logger.error(exception.engineerMessage)
        Status(exception.httpCode)(exception.userMessage)
      case Right(success) =>
        logger.info(successLogMessage)
        successResult(success)
    }
  }
}
