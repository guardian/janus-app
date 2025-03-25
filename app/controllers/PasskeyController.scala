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
import com.webauthn4j.data._
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.challenge.{Challenge, DefaultChallenge}
import play.api.libs.json.{JsNumber, JsString, Json, Writes}
import play.api.mvc._

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import scala.jdk.CollectionConverters._

class PasskeyController(controllerComponents: ControllerComponents)
    extends AbstractController(controllerComponents) {

  implicit val publicKeyCredentialTypeWrites: Writes[PublicKeyCredentialType] =
    Writes(`type` => JsString(`type`.getValue))

  implicit val coseAlgorithmIdentifierWrites: Writes[COSEAlgorithmIdentifier] =
    Writes(alg => JsNumber(alg.getValue()))

  implicit val publicKeyCredentialParametersWrites
      : Writes[PublicKeyCredentialParameters] =
    Writes(params =>
      Json.obj(
        "type" -> params.getType,
        "alg" -> params.getAlg
      )
    )

  implicit val publicKeyCredentialRpEntityWrites
      : Writes[PublicKeyCredentialRpEntity] =
    Writes(rp =>
      Json.obj(
        "id" -> rp.getId,
        "name" -> rp.getName
      )
    )

  implicit val publicKeyCredentialUserEntityWrites
      : Writes[PublicKeyCredentialUserEntity] =
    Writes(user =>
      Json.obj(
        "id" -> Base64.getUrlEncoder.withoutPadding
          .encodeToString(user.getId),
        "name" -> user.getName,
        "displayName" -> user.getDisplayName
      )
    )

  implicit val challengeWrites: Writes[Challenge] =
    Writes(challenge =>
      JsString(
        Base64.getUrlEncoder.withoutPadding
          .encodeToString(challenge.getValue)
      )
    )

  implicit val publicKeyCredentialCreationOptionsWrites
      : Writes[PublicKeyCredentialCreationOptions] =
    Writes(options =>
      Json.obj(
        "challenge" -> options.getChallenge,
        "rp" -> options.getRp,
        "user" -> options.getUser,
        "pubKeyCredParams" -> options.getPubKeyCredParams.asScala
      )
    )

  def registrationOptions: Action[AnyContent] = Action { _ =>
    val appDomain = "example.com"
    val appName = "Janus"
    val userId = "user-id"
    val userName = "user-name"
    val userDisplayName = "user-display-name"
    val alg = COSEAlgorithmIdentifier.ES256
    val rp = new PublicKeyCredentialRpEntity(appDomain, appName)
    val user = new PublicKeyCredentialUserEntity(
      userId.getBytes(UTF_8),
      userName,
      userDisplayName
    )
    val challenge = new DefaultChallenge()
    val pubKeyCredParams = List(
      new PublicKeyCredentialParameters(
        PublicKeyCredentialType.PUBLIC_KEY,
        alg
      )
    ).asJava
    val options = new PublicKeyCredentialCreationOptions(
      rp,
      user,
      challenge,
      pubKeyCredParams
    )
    Ok(Json.toJson(options))
  }
}
