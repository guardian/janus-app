package logic

import cats.implicits.catsSyntaxMonadError
import com.gu.googleauth.UserIdentity
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.credential.{CredentialRecord, CredentialRecordImpl}
import com.webauthn4j.data.*
import com.webauthn4j.data.AttestationConveyancePreference.DIRECT
import com.webauthn4j.data.PublicKeyCredentialType.PUBLIC_KEY
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.Challenge
import com.webauthn4j.data.extension.client.*
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.util.Base64UrlUtil
import com.webauthn4j.verifier.exception.VerificationException
import models.*

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.duration.{Duration, SECONDS}
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Try}

/** Logic for registration of passkeys and authentication using them. */
object Passkey {

  /* When true, requires the user to verify their identity
   * (with Touch ID or other authentication method)
   * before completing registration and authentication.
   */
  private val userVerificationRequired = true

  // In order of algorithms we prefer
  private val publicKeyCredentialParameters = List(
    // EdDSA for better security/performance in newer authenticators
    new PublicKeyCredentialParameters(
      PUBLIC_KEY,
      COSEAlgorithmIdentifier.EdDSA
    ),
    // ES256 is widely supported and efficient
    new PublicKeyCredentialParameters(
      PUBLIC_KEY,
      COSEAlgorithmIdentifier.ES256
    ),
    // RS256 for broader compatibility
    new PublicKeyCredentialParameters(
      PUBLIC_KEY,
      COSEAlgorithmIdentifier.RS256
    )
  )

  private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()

  private def toDescriptor(
      passkey: PasskeyMetadata
  ): PublicKeyCredentialDescriptor = {
    val credType = PUBLIC_KEY
    val id = Base64UrlUtil.decode(passkey.id)
    // Include common transport types to help the authenticator find the right credential
    val transports = Set(
      AuthenticatorTransport.INTERNAL, // Platform authenticators
      AuthenticatorTransport.HYBRID, // QR code and proximity-based
      AuthenticatorTransport.USB, // USB security keys
      AuthenticatorTransport.NFC // NFC-based authenticators
    )
    new PublicKeyCredentialDescriptor(credType, id, transports.asJava)
  }

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
    * @param existingPasskeys
    *   The credentials that the user already has, to avoid duplicate
    *   registration.
    * @return
    *   A PublicKeyCredentialCreationOptions object containing the registration
    *   options.
    */
  def registrationOptions(
      appName: String,
      appHost: String,
      user: UserIdentity,
      challenge: Challenge,
      existingPasskeys: Seq[PasskeyMetadata]
  ): Try[PublicKeyCredentialCreationOptions] =
    Try {
      val appDomain = URI.create(appHost).getHost
      val relyingParty = new PublicKeyCredentialRpEntity(appDomain, appName)
      val userInfo = new PublicKeyCredentialUserEntity(
        user.username.getBytes(UTF_8),
        user.username,
        user.fullName
      )
      val timeout = Duration(60, SECONDS)
      val excludeCredentials = existingPasskeys.map(toDescriptor)
      val authenticatorSelection = new AuthenticatorSelectionCriteria(
        AuthenticatorAttachment.PLATFORM, // Prefer platform authenticators (TouchID, FaceID, Windows Hello)
        ResidentKeyRequirement.PREFERRED, // Store credentials on the authenticator when possible
        UserVerificationRequirement.REQUIRED // Always require user verification
      )
      val hints: Seq[PublicKeyCredentialHints] = Nil
      val attestation = DIRECT
      val extensions: AuthenticationExtensionsClientInputs[
        RegistrationExtensionClientInput
      ] = null
      new PublicKeyCredentialCreationOptions(
        relyingParty,
        userInfo,
        challenge,
        publicKeyCredentialParameters.asJava,
        timeout.toMillis,
        excludeCredentials.asJava,
        authenticatorSelection,
        hints.asJava,
        attestation,
        extensions
      )
    }.adaptError(err =>
      JanusException.invalidFieldInRequest(user, "registration options", err)
    )

  /** Options required by a browser to initiate the authentication process.
    *
    * See
    * [[https://webauthn4j.github.io/webauthn4j/en/#generating-a-webauthn-assertion]].
    */
  def authenticationOptions(
      appHost: String,
      user: UserIdentity,
      challenge: Challenge,
      existingPasskeys: Seq[PasskeyMetadata]
  ): Try[PublicKeyCredentialRequestOptions] = {
    if (existingPasskeys.isEmpty) {
      Failure(JanusException.noPasskeysRegistered(user))
    } else {
      Try {
        val timeout = Duration(60, SECONDS)
        val rpId = URI.create(appHost).getHost
        val allowCredentials = existingPasskeys.map(toDescriptor)
        val userVerification = UserVerificationRequirement.REQUIRED
        val hints = Nil
        val extensions: AuthenticationExtensionsClientInputs[
          AuthenticationExtensionClientInput
        ] = null
        new PublicKeyCredentialRequestOptions(
          challenge,
          timeout.toMillis,
          rpId,
          allowCredentials.asJava,
          userVerification,
          hints.asJava,
          extensions
        )
      }.adaptError(err =>
        JanusException.invalidFieldInRequest(
          user,
          "authentication options",
          err
        )
      )
    }
  }

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
      user: UserIdentity,
      challenge: Challenge,
      jsonResponse: String
  ): Try[CredentialRecord] =
    Try {
      val regData = webAuthnManager.parseRegistrationResponseJSON(jsonResponse)
      val regParams = new RegistrationParameters(
        new ServerProperty(
          Origin.create(appHost),
          URI.create(appHost).getHost,
          challenge
        ),
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
    }.adaptError {
      case err: VerificationException =>
        JanusException.invalidFieldInRequest(user, "passkey", err)
      case err =>
        JanusException.invalidFieldInRequest(user, "passkey", err)
    }

  /** Parses the authentication response from the browser. Call this when the
    * user has authenticated to the browser using a passkey.
    *
    * See
    * [[https://webauthn4j.github.io/webauthn4j/en/#webauthn-assertion-verification-and-post-processing]].
    *
    * @param jsonResponse
    *   Json response from browser containing authentication data.
    */
  def parsedAuthentication(
      user: UserIdentity,
      jsonResponse: String
  ): Try[AuthenticationData] =
    Try(webAuthnManager.parseAuthenticationResponseJSON(jsonResponse))
      .adaptError {
        case err: DataConversionException =>
          JanusException.invalidFieldInRequest(user, "passkey", err)
        case err =>
          JanusException.invalidFieldInRequest(user, "passkey", err)
      }

  /** Verifies the authentication response from the browser. Call this when the
    * user has authenticated to the browser using a passkey.
    *
    * See
    * [[https://webauthn4j.github.io/webauthn4j/en/#webauthn-assertion-verification-and-post-processing]].
    *
    * @param challenge
    *   Must correspond with the challenge passed in [[authenticationOptions]]).
    * @param authenticationData
    *   The parsed authentication data supplied by the browser.
    */
  def verifiedAuthentication(
      appHost: String,
      user: UserIdentity,
      challenge: Challenge,
      authenticationData: AuthenticationData,
      credentialRecord: CredentialRecord
  ): Try[AuthenticationData] =
    Try {
      val authParams = new AuthenticationParameters(
        new ServerProperty(
          Origin.create(appHost),
          URI.create(appHost).getHost,
          challenge
        ),
        credentialRecord,
        List(authenticationData.getCredentialId).asJava,
        userVerificationRequired
      )
      webAuthnManager.verify(authenticationData, authParams)
    }.adaptError {
      case err: VerificationException =>
        JanusException.authenticationFailure(user, err)
      case err =>
        JanusException.invalidFieldInRequest(user, "passkey", err)
    }
}
