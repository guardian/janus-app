package models

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.module.SimpleModule
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.util.Base64UrlUtil

import java.time.Instant

case class PasskeyMetadata(
    id: String,
    name: String,
    registrationTime: Instant,
    // Identifies the model of the authenticator device that created the passkey
    aaguid: AAGUID,
    lastUsedTime: Option[Instant]
)

object PasskeyEncodings {

  /*
   * As the webauthn library uses Jackson annotations to generate JSON encodings,
   * we might as well use them instead of generating our own JSON models.
   * This will help with futureproofing.
   */
  val mapper: ObjectMapper = {
    val mapper = new ObjectMapper()
    val module = new SimpleModule()
    /*
     * Serialize just the value of the challenge instead of a nested object.
     * Not sure why this hasn't been encoded in the form the webauthn spec expects.
     */
    module.addSerializer(
      classOf[DefaultChallenge],
      (
          challenge: DefaultChallenge,
          gen: JsonGenerator,
          _: SerializerProvider
      ) => gen.writeString(Base64UrlUtil.encodeToString(challenge.getValue))
    )
    mapper.registerModule(module)
    mapper
  }
}
