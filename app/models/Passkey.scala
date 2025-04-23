package models

import com.webauthn4j.data._
import com.webauthn4j.data.client.challenge.Challenge
import com.webauthn4j.util.Base64UrlUtil
import io.circe.syntax._
import io.circe.{Encoder, Json}

import scala.jdk.CollectionConverters._

/** Encodings for the WebAuthn data types used in passkey registration and
  * authentication. These can't be auto-encoded by Circe because they aren't
  * case classes.
  */
object Passkey {

  implicit val relyingPartyEncoder: Encoder[PublicKeyCredentialRpEntity] =
    Encoder.forProduct2("id", "name")(rp => (rp.getId, rp.getName))

  implicit val userInfoEncoder: Encoder[PublicKeyCredentialUserEntity] =
    Encoder.forProduct3("id", "name", "displayName")(user =>
      (
        Base64UrlUtil.encodeToString(user.getId),
        user.getName,
        user.getDisplayName
      )
    )

  implicit val publicKeyCredentialParametersEncoder
      : Encoder[PublicKeyCredentialParameters] =
    Encoder.forProduct2("type", "alg")(param =>
      (param.getType.getValue, param.getAlg.getValue)
    )

  implicit val challengeEncoder: Encoder[Challenge] =
    Encoder.instance(challenge =>
      Json.fromString(Base64UrlUtil.encodeToString(challenge.getValue))
    )

  implicit val publicKeyCredentialParametersListEncoder
      : Encoder[java.util.List[PublicKeyCredentialParameters]] =
    Encoder.instance(paramsList =>
      Json.fromValues(paramsList.asScala.map(_.asJson))
    )

  implicit val creationOptionsEncoder
      : Encoder[PublicKeyCredentialCreationOptions] =
    Encoder.forProduct4("challenge", "rp", "user", "pubKeyCredParams")(
      options =>
        (
          options.getChallenge,
          options.getRp,
          options.getUser,
          options.getPubKeyCredParams
        )
    )

  implicit val requestOptionsEncoder
      : Encoder[PublicKeyCredentialRequestOptions] =
    Encoder.forProduct1("challenge")(_.getChallenge)
}
