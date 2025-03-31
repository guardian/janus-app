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

  /* When true, requires the user to verify their identity
   * (with Touch ID or other authentication method)
   * before completing the registration.
   */
  private val userVerificationRequired = true

  private val publicKeyCredentialParameters
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
  ): Try[PublicKeyCredentialCreationOptions] = {
    for {
      appDomain <- Try(URI.create(appHost).getHost)

      relyingParty <- Try(new PublicKeyCredentialRpEntity(appDomain, appName))

      userInfo <- Try(
        new PublicKeyCredentialUserEntity(
          user.username.getBytes(UTF_8),
          user.username,
          user.fullName
        )
      )

      options <- Try(
        new PublicKeyCredentialCreationOptions(
          relyingParty,
          userInfo,
          challenge,
          publicKeyCredentialParameters
        )
      )
    } yield options
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
  ): Try[CredentialRecord] = {
    for {
      regData <- Try(
        webAuthnManager.parseRegistrationResponseJSON(jsonResponse)
      )

      origin <- Try(Origin.create(appHost))

      relyingPartyId <- Try(URI.create(appHost).getHost)

      serverProps <- Try(
        new ServerProperty(
          origin,
          relyingPartyId,
          new DefaultChallenge(challenge)
        )
      )

      regParams = new RegistrationParameters(
        serverProps,
        publicKeyCredentialParameters,
        userVerificationRequired
      )

      _ <- Try(webAuthnManager.verify(regData, regParams))
    } yield new CredentialRecordImpl(
      regData.getAttestationObject,
      regData.getCollectedClientData,
      regData.getClientExtensions,
      regData.getTransports
    )
  }
}
