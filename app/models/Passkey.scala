package models

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.module.SimpleModule
import com.webauthn4j.data._
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
      new JsonSerializer[DefaultChallenge] {
        override def serialize(
            challenge: DefaultChallenge,
            gen: JsonGenerator,
            serializers: SerializerProvider
        ): Unit =
          gen.writeString(Base64UrlUtil.encodeToString(challenge.getValue))
      }
    )
    /*
     * Serialize so that id is base64url encoded, as the webauthn spec demands.
     */
    module.addSerializer(
      classOf[PublicKeyCredentialUserEntity],
      new JsonSerializer[PublicKeyCredentialUserEntity] {
        override def serialize(
            user: PublicKeyCredentialUserEntity,
            gen: JsonGenerator,
            serializers: SerializerProvider
        ): Unit = {
          gen.writeStartObject()
          gen.writeStringField("id", Base64UrlUtil.encodeToString(user.getId))
          gen.writeStringField("name", user.getName)
          gen.writeStringField("displayName", user.getDisplayName)
          gen.writeEndObject()
        }
      }
    )
    /*
     * Again, serialize so that id is base64url encoded, as the webauthn spec demands.
     */
    module.addSerializer(
      classOf[PublicKeyCredentialDescriptor],
      new JsonSerializer[PublicKeyCredentialDescriptor] {
        override def serialize(
            descriptor: PublicKeyCredentialDescriptor,
            gen: JsonGenerator,
            serializers: SerializerProvider
        ): Unit = {
          gen.writeStartObject()
          gen.writeStringField("type", descriptor.getType.getValue)
          gen.writeStringField(
            "id",
            Base64UrlUtil.encodeToString(descriptor.getId)
          )
          if (descriptor.getTransports != null) {
            gen.writeArrayFieldStart("transports")
            descriptor.getTransports.forEach(transport =>
              gen.writeString(transport.getValue)
            )
            gen.writeEndArray()
          }
          gen.writeEndObject()
        }
      }
    )
    mapper.registerModule(module)
    mapper
  }
}
