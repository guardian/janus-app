package models

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.webauthn4j.data.*
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.util.Base64UrlUtil
import play.api.Logging
import play.api.libs.functional.syntax.*
import play.api.libs.json.{JsError, JsPath, JsSuccess, Json, Reads}

import java.time.Instant
import scala.io.Source
import scala.util.{Failure, Success, Try, Using}

case class PasskeyMetadata(
    id: String,
    name: String,
    registrationTime: Instant,
    // Identifies the model of the authenticator device that created the passkey
    aaguid: AAGUID,
    lastUsedTime: Option[Instant],
    authenticator: Option[PasskeyAuthenticator]
)

enum PasskeyFlow:
  case Registration, Authentication

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

/** Represents metadata about a passkey authenticator device.
  *
  * @param description
  *   A human-readable description of the authenticator device (e.g., "YubiKey 5
  *   Series", "Touch ID", "Windows Hello")
  * @param icon
  *   Optional Base64 PNG image
  */
case class PasskeyAuthenticator(description: String, icon: Option[String])

/** Handles loading and parsing of authenticator metadata from resource files
  * containing a mapping of AAGUID (Authenticator Attestation Globally Unique
  * Identifier) values to their corresponding authenticator descriptions and
  * icons.
  *
  * The AAGUID is a 128-bit identifier that indicates the type (e.g. make and
  * model) of an authenticator, allowing relying parties to identify and
  * potentially restrict specific authenticator models.
  *
  * This parses the raw community authenticator database published at
  * [[https://github.com/passkeydeveloper/passkey-authenticator-aaguids]], whose
  * entries use `name` for the description and `icon_light`/`icon_dark` for the
  * icons. It is used as a fallback for authenticators (such as platform
  * authenticators) that are not published to the FIDO Metadata Service.
  *
  * Example usage:
  * {{{
  * val authenticators = PasskeyAuthenticator.fromResource("passkeys_aaguid_community.json")
  * val authenticator = authenticators.get(someAAGUID)
  * }}}
  */
object PasskeyAuthenticator extends Logging {

  private case class AuthenticatorError(
      message: String,
      cause: Option[Throwable] = None
  )

  // AAGUIDs are represented as hyphenated UUID strings in JSON.
  given Reads[AAGUID] = Reads(
    _.validate[String].flatMap { value =>
      Try(new AAGUID(value)) match {
        case Success(aaguid) => JsSuccess(aaguid)
        case Failure(e) => JsError(s"Invalid AAGUID format: ${e.getMessage}")
      }
    }
  )

  /* Maps the community database entry format (`name`, `icon_light`) onto the
   * PasskeyAuthenticator model. `icon_dark` is intentionally ignored. */
  given Reads[PasskeyAuthenticator] =
    ((JsPath \ "name").read[String] and
      (JsPath \ "icon_light").readNullable[String])(
      PasskeyAuthenticator.apply
    )

  /** Loads authenticator metadata from a JSON resource file.
    *
    * Reads a JSON file from the classpath that contains a mapping of AAGUID
    * strings to community authenticator entries. This is used to load
    * community-provided metadata about known authenticator devices.
    *
    * @param resourcePath
    *   The classpath path to the JSON resource file
    * @return
    *   A map from AAGUID to PasskeyAuthenticator metadata. Returns empty map on
    *   failure.
    *
    * @example
    *   {{{
    * val authenticators = PasskeyAuthenticator.fromResource("passkeys_aaguid_community.json")
    * // Returns Map[AAGUID, PasskeyAuthenticator] with loaded authenticator metadata
    *   }}}
    */
  def fromResource(resourcePath: String): Map[AAGUID, PasskeyAuthenticator] = {
    (for {
      content <- loadResourceContent(resourcePath)
      authenticators <- parseAuthenticators(content)
    } yield authenticators) match {
      case Left(e) =>
        logger.error(
          s"Passkey authenticator data load failed: ${e.message}",
          e.cause.orNull
        )
        Map.empty
      case Right(data) =>
        logger.info(
          s"Successfully loaded ${data.size} passkey authenticator data"
        )
        data
    }
  }

  /** Loads the content of a resource file as a string. */
  private def loadResourceContent(
      resourcePath: String
  ): Either[AuthenticatorError, String] = {
    Using(Source.fromResource(resourcePath))(_.getLines.mkString).toEither.left
      .map(e =>
        AuthenticatorError(s"Failed to read resource: $resourcePath", Some(e))
      )
  }

  /** Parses a JSON string into a map of AAGUID to PasskeyAuthenticator.
    *
    * Takes a JSON string representation and attempts to parse it into the
    * expected data structure. The JSON should contain an object where keys are
    * AAGUID strings and values are community authenticator entries.
    *
    * @param jsonString
    *   The JSON string to parse
    * @return
    *   Either an AuthenticatorError on parsing failure, or the parsed map on
    *   success
    */
  private def parseAuthenticators(
      jsonString: String
  ): Either[AuthenticatorError, Map[AAGUID, PasskeyAuthenticator]] = {
    for {
      js <- Try(Json.parse(jsonString)).toEither.left.map(e =>
        AuthenticatorError(s"Failed to parse JSON: ${e.getMessage}", Some(e))
      )
      authenticators <- js.validate[Map[AAGUID, PasskeyAuthenticator]] match {
        case JsSuccess(data, _) => Right(data)
        case JsError(errors)    =>
          Left(
            AuthenticatorError(
              JsonErrorFormatter.formatJsErrors(errors, jsonString)
            )
          )
      }
    } yield authenticators
  }
}
