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

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

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

  private val appName = "Janus"

  private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()

  /** Creates registration options for a new passkey. This is required by a
    * browser to initiate the registration process.
    *
    * See
    * [[https://webauthn4j.github.io/webauthn4j/en/#generating-a-webauthn-credential-key-pair]].
    *
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
    *   Must correspond
    *   with the challenge passed in [[registrationOptions]]).
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
}
