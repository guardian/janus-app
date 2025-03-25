package controllers

import com.webauthn4j.WebAuthnManager
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.data._
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.{Challenge, DefaultChallenge}
import com.webauthn4j.server.ServerProperty
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

  val alg = COSEAlgorithmIdentifier.ES256

  val pubKeyCredParams = List(
    new PublicKeyCredentialParameters(
      PublicKeyCredentialType.PUBLIC_KEY,
      alg
    )
  ).asJava

  val appDomain = "example.com"
  val appName = "Janus"

  def registrationOptions: Action[AnyContent] = Action { request =>
    val userId = "user-id"
    val userName = "user-name"
    val userDisplayName = "user-display-name"
    val rp = new PublicKeyCredentialRpEntity(appDomain, appName)
    val user = new PublicKeyCredentialUserEntity(
      userId.getBytes(UTF_8),
      userName,
      userDisplayName
    )
    val challenge = new DefaultChallenge()
    val options = new PublicKeyCredentialCreationOptions(
      rp,
      user,
      challenge,
      pubKeyCredParams
    )

    val challengeBase64 = Base64.getUrlEncoder.withoutPadding.encodeToString(challenge.getValue)

    Ok(Json.toJson(options))
      .withSession(request.session + ("passkey-challenge" -> challengeBase64))
  }

  def register: Action[AnyContent] = Action { request =>
    val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()
    val regData = webAuthnManager.parseRegistrationResponseJSON("TODO")
    val origin = Origin.create(appDomain)
    val rpId = appDomain

    val challengeBase64 = request.session.get("passkey-challenge").get

    val challengeBytes = Base64.getUrlDecoder.decode(challengeBase64)
    val challenge = new DefaultChallenge(challengeBytes)
    val serverProps = new ServerProperty(origin, rpId, challenge)
    val userVerificationRequired = false
    val userPresenceRequired = true
    val regParams = new RegistrationParameters(serverProps, pubKeyCredParams, userVerificationRequired, userPresenceRequired)
    webAuthnManager.verify(regData, regParams)
    //    val credRecord = new CredentialRecordImpl()
    //    save(credRecord)
    Ok("TODO")
      .withSession(request.session - "passkey-challenge")
  }

  def save(credRecord: CredentialRecord): Unit = ???
}
