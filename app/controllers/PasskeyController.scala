package controllers

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
