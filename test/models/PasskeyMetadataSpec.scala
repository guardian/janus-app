package models

import com.webauthn4j.data.AuthenticatorTransport
import com.webauthn4j.data.attestation.authenticator.AAGUID
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util.UUID

class PasskeyMetadataSpec extends AnyFlatSpec with Matchers {

  private val baseMetadata = PasskeyMetadata(
    id = "test-id",
    name = "Test Passkey",
    registrationTime = Instant.now(),
    aaguid = new AAGUID(UUID.randomUUID()),
    transports = Seq.empty,
    lastUsedTime = None,
    authenticator = None
  )

  "isBoolean" should "return true when transports contains only INTERNAL" in {
    val metadata = baseMetadata.copy(transports = Seq(AuthenticatorTransport.INTERNAL))
    metadata.isPlatform shouldBe true
  }

  it should "return false when transports contains INTERNAL and other transports" in {
    val metadata = baseMetadata.copy(transports = Seq(
      AuthenticatorTransport.INTERNAL,
      AuthenticatorTransport.BLE
    ))
    metadata.isPlatform shouldBe false
  }

  it should "return false when transports contains a single non-INTERNAL transport" in {
    val metadata = baseMetadata.copy(transports = Seq(AuthenticatorTransport.USB))
    metadata.isPlatform shouldBe false
  }

  it should "return false when transports is empty" in {
    val metadata = baseMetadata.copy(transports = Seq.empty)
    metadata.isPlatform shouldBe false
  }
}
