package models

import com.webauthn4j.data._
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.client.challenge.Challenge
import com.webauthn4j.util.Base64UrlUtil
import play.api.libs.json._

import java.time.Instant
import scala.jdk.CollectionConverters._

case class PasskeyMetadata(
    id: String,
    name: String,
    registrationTime: Instant,
    // Identifies the model of the authenticator device that created the passkey
    aaguid: AAGUID
)

/** Encodings for the WebAuthn data types used in passkey registration and
  * authentication. These can't be auto-encoded because they aren't case
  * classes.
  */
object PasskeyEncodings {

  implicit val relyingPartyWrites: Writes[PublicKeyCredentialRpEntity] =
    Writes { rp =>
      Json.obj(
        "id" -> rp.getId,
        "name" -> rp.getName
      )
    }

  implicit val userInfoWrites: Writes[PublicKeyCredentialUserEntity] =
    Writes { user =>
      Json.obj(
        "id" -> Base64UrlUtil.encodeToString(user.getId),
        "name" -> user.getName,
        "displayName" -> user.getDisplayName
      )
    }

  implicit val publicKeyCredentialParametersWrites
      : Writes[PublicKeyCredentialParameters] =
    Writes { param =>
      Json.obj(
        "type" -> param.getType.getValue,
        "alg" -> param.getAlg.getValue
      )
    }

  implicit val challengeWrites: Writes[Challenge] =
    Writes { challenge =>
      JsString(Base64UrlUtil.encodeToString(challenge.getValue))
    }

  implicit val publicKeyCredentialParametersListWrites
      : Writes[java.util.List[PublicKeyCredentialParameters]] =
    Writes { paramsList =>
      JsArray(paramsList.asScala.map(Json.toJson(_)).toSeq)
    }

  implicit val creationOptionsWrites
      : Writes[PublicKeyCredentialCreationOptions] =
    Writes { options =>
      Json.obj(
        "challenge" -> options.getChallenge,
        "rp" -> options.getRp,
        "user" -> options.getUser,
        "pubKeyCredParams" -> options.getPubKeyCredParams
      )
    }

  implicit val requestOptionsWrites: Writes[PublicKeyCredentialRequestOptions] =
    Writes { options =>
      Json.obj(
        "challenge" -> options.getChallenge
      )
    }
}
