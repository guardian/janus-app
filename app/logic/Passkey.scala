package logic

import com.gu.googleauth.UserIdentity
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.credential.{CredentialRecord, CredentialRecordImpl}
import com.webauthn4j.data._
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.{Challenge, DefaultChallenge}
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.verifier.exception.VerificationException
import models._
import play.api.http.Status.BAD_REQUEST

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8

/** Logic for registration of passkeys and authentication using them. */
object Passkey {

  /* When true, requires the user to verify their identity
   * (with Touch ID or other authentication method)
   * before completing the registration.
   */
  private val userVerificationRequired = true

  private val publicKeyCredentialParameters = List(
    // ES256 is widely supported and efficient
    new PublicKeyCredentialParameters(
      PublicKeyCredentialType.PUBLIC_KEY,
      COSEAlgorithmIdentifier.ES256
    ),
    // RS256 for broader compatibility
    new PublicKeyCredentialParameters(
      PublicKeyCredentialType.PUBLIC_KEY,
      COSEAlgorithmIdentifier.RS256
    ),
    // EdDSA for better security/performance in newer authenticators
    new PublicKeyCredentialParameters(
      PublicKeyCredentialType.PUBLIC_KEY,
      COSEAlgorithmIdentifier.EdDSA
    )
  )

  private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()

  /** Creates registration options for a new passkey. This is required by a
    * browser to initiate the registration process.
    *
    * See
    * [[https://webauthn4j.github.io/webauthn4j/en/#generating-a-webauthn-credential-key-pair]].
    *
    * @param appName
    *   Name of the app the passkey will authenticate (the relying party).
    * @param appHost
    *   The host of the application the passkey will authenticate (the relying
    *   party).
    * @param user
    *   The user identity retrieved from Google auth.
    * @param challenge
    *   The challenge to be used for registration.
    * @return
    *   A PublicKeyCredentialCreationOptions object containing the registration
    *   options.
    */
  // TODO: move out
  private object config {
    /* When true, requires the user to verify their identity
     * (with Touch ID or other authentication method)
     * before completing the registration.
     */
    val userVerificationRequired = true

    val publicKeyCredentialParameters
    : util.List[PublicKeyCredentialParameters] = List(
      // ES256 is widely supported and efficient
      new PublicKeyCredentialParameters(
        PublicKeyCredentialType.PUBLIC_KEY,
        COSEAlgorithmIdentifier.ES256
      ),
      // RS256 for broader compatibility
      new PublicKeyCredentialParameters(
        PublicKeyCredentialType.PUBLIC_KEY,
        COSEAlgorithmIdentifier.RS256
      ),
      // EdDSA for better security/performance in newer authenticators
      new PublicKeyCredentialParameters(
        PublicKeyCredentialType.PUBLIC_KEY,
        COSEAlgorithmIdentifier.EdDSA
      )
    ).asJava
  }

  sealed trait PasskeyFailure {
    def details: String

    def cause: Throwable
  }

  case class InvalidInputFailure(details: String, cause: Throwable)
    extends PasskeyFailure

  case class VerificationFailure(details: String, cause: Throwable)
    extends PasskeyFailure

  case class StorageFailure(details: String, cause: Throwable)
    extends PasskeyFailure

  // TODO: look into how this is configured
  private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()

  /** Creates registration options for a new passkey. This is required by a
   * browser to initiate the registration process.
   *
   * @param appName
   * The name of the application the passkey will authenticate (the relying
   * party).
   * @param appHost
   * The host of the application the passkey will authenticate (the relying
   * party).
   * @param user
   * The user identity retrieved from Google auth.
   * @param challenge
   * The challenge to be used for registration.
   * @return
   * A PublicKeyCredentialCreationOptions object containing the registration
   * options.
   */
  def registrationOptions(
      appName: String,
      appHost: String,
      user: UserIdentity,
      challenge: Challenge = new DefaultChallenge()
  ): Try[PublicKeyCredentialCreationOptions] =
    Try {
      val appDomain = URI.create(appHost).getHost
      val relyingParty = new PublicKeyCredentialRpEntity(appDomain, appName)
      val userInfo = new PublicKeyCredentialUserEntity(
        user.username.getBytes(UTF_8),
        user.username,
        user.fullName
      )
      new PublicKeyCredentialCreationOptions(
        relyingParty,
        userInfo,
        challenge,
        publicKeyCredentialParameters.asJava
      )
    }.recoverWith(exception =>
      Failure(
        JanusException(
          userMessage = "Failed to create registration options",
          engineerMessage =
            s"Failed to create registration options for user ${user.username}: ${exception.getMessage}",
          httpCode = BAD_REQUEST,
          causedBy = Some(exception)
        )
      )
    )

  /** Verifies the registration response from the browser. This is called after
    * the user has completed the passkey creation process on the browser.
    *
    * See
    * [[https://webauthn4j.github.io/webauthn4j/en/#registering-the-webauthn-public-key-credential-on-the-server]].
    *
    * @param appHost
    *   The host of the application the passkey will authenticate (the relying
    *   party).
    * @param challenge
    *   Must correspond with the challenge passed in [[registrationOptions]]).
    * @param jsonResponse
    *   The JSON response from the browser containing the registration data.
    * @return
    *   A CredentialRecord object containing the verified credential data or an
    *   [[IllegalArgumentException]] if verification fails.
    */
  def verifiedRegistration(
      appHost: String,
      challenge: Challenge,
      jsonResponse: String
  ): Try[CredentialRecord] =
    Try {
      val regData = webAuthnManager.parseRegistrationResponseJSON(jsonResponse)
      val origin = Origin.create(appHost)
      val relyingPartyId = URI.create(appHost).getHost
      val serverProps = new ServerProperty(
        origin,
        relyingPartyId,
        challenge
      )
      val regParams = new RegistrationParameters(
        serverProps,
        publicKeyCredentialParameters.asJava,
        userVerificationRequired
      )
      val verified = webAuthnManager.verify(regData, regParams)
      new CredentialRecordImpl(
        verified.getAttestationObject,
        verified.getCollectedClientData,
        verified.getClientExtensions,
        verified.getTransports
      )
    } recoverWith {
      case exception: VerificationException =>
        Failure(
          JanusException(
            userMessage = "Registration verification failed",
            engineerMessage =
              s"Registration verification failed: ${exception.getMessage}",
            httpCode = BAD_REQUEST,
            causedBy = Some(exception)
          )
        )
      case exception =>
        Failure(
          JanusException(
            userMessage = "Bad arguments for verification request",
            engineerMessage =
              s"Bad arguments for verification request: ${exception.getMessage}",
            httpCode = BAD_REQUEST,
            causedBy = Some(exception)
          )
        )
    }
                         ): Either[InvalidInputFailure, PublicKeyCredentialCreationOptions] = {
    for {
      appDomain <- domainOf(appHost)

      relyingParty <- Try(
        new PublicKeyCredentialRpEntity(appDomain, appName)
      ).toEither.left.map(exception =>
        InvalidInputFailure("Invalid relying party fields", exception)
      )

      userInfo <- Try(
        new PublicKeyCredentialUserEntity(
          user.username.getBytes(UTF_8),
          user.username,
          user.fullName
        )
      ).toEither.left.map(exception =>
        InvalidInputFailure("Invalid user fields", exception)
      )

      options <- Try(
        new PublicKeyCredentialCreationOptions(
          relyingParty,
          userInfo,
          challenge,
          config.publicKeyCredentialParameters
        )
      ).toEither.left.map(exception =>
        InvalidInputFailure("Failed to create registration options", exception)
      )
    } yield options
  }

  /** Verifies the registration response from the browser. This is called after
   * the user has completed the registration process on the browser.
   *
   * @param appHost
   * The host of the application the passkey will authenticate (the relying
   * party).
   * @param challenge
   * Base 64 encoded challenge string used for registration (must correspond
   * with the challenge passed in [[registrationOptions]]).
   * @param jsonResponse
   * The JSON response from the browser containing the registration data.
   * @return
   * A CredentialRecord object containing the verified credential data or an
   * [[IllegalArgumentException]] if verification fails.
   */
  def verifiedRegistration(
      appHost: String,
      challenge: String,
      jsonResponse: String
                          ): Either[PasskeyFailure, CredentialRecord] = {
    for {
      regData <- Try(
        webAuthnManager.parseRegistrationResponseJSON(jsonResponse)
      ).toEither.left.map(exception =>
        InvalidInputFailure("Invalid registration response", exception)
      )

      origin <- Try(Origin.create(appHost)).toEither.left.map(exception =>
        InvalidInputFailure("Invalid origin", exception)
      )

      relyingPartyId <- domainOf(appHost)

      serverProps <- Try(
        new ServerProperty(
          origin,
          relyingPartyId,
          new DefaultChallenge(challenge)
        )
      ).toEither.left.map(exception =>
        InvalidInputFailure("Invalid server properties", exception)
      )

      regParams = new RegistrationParameters(
        serverProps,
        config.publicKeyCredentialParameters,
        config.userVerificationRequired
      )

      _ <- Try(webAuthnManager.verify(regData, regParams)).toEither.left.map(
        exception =>
          VerificationFailure("Registration verification failed", exception)
      )
    } yield {
      new CredentialRecordImpl(
        regData.getAttestationObject,
        regData.getCollectedClientData,
        regData.getClientExtensions,
        regData.getTransports
      )
    }
  }

  private def domainOf(host: String) =
    Try(URI.create(host).getHost).toEither.left.map(exception =>
      InvalidInputFailure(s"Invalid host: $host", exception)
    )

  // TODO
  def store(
             user: UserIdentity,
             credentialRecord: CredentialRecord
           ): Either[StorageFailure, Unit] = {
    println(
      s"TODO: Store credential record: ${user.username}: $credentialRecord"
    )
    Right(())
  }
}
