package logic

import com.gu.googleauth.UserIdentity
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.credential.{CredentialRecord, CredentialRecordImpl}
import com.webauthn4j.data._
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.{Challenge, DefaultChallenge}
import com.webauthn4j.server.ServerProperty

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.util
import scala.jdk.CollectionConverters._
import scala.util.Try

/** Logic for registration of passkeys and authentication using them. */
object Passkey {

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
    *   The name of the application the passkey will authenticate (the relying
    *   party).
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
  def registrationOptions(
      appName: String,
      appHost: String,
      user: UserIdentity,
      challenge: Challenge = new DefaultChallenge()
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
    *   The host of the application the passkey will authenticate (the relying
    *   party).
    * @param challenge
    *   Base 64 encoded challenge string used for registration (must correspond
    *   with the challenge passed in [[registrationOptions]]).
    * @param jsonResponse
    *   The JSON response from the browser containing the registration data.
    * @return
    *   A CredentialRecord object containing the verified credential data or an
    *   [[IllegalArgumentException]] if verification fails.
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
    } yield new CredentialRecordImpl(
      regData.getAttestationObject,
      regData.getCollectedClientData,
      regData.getClientExtensions,
      regData.getTransports
    )
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
