package services

import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.metadata.{
  FidoMDS3MetadataBLOBProvider,
  MetadataBLOBBasedMetadataStatementRepository
}
import com.webauthn4j.metadata.data.statement.MetadataStatement
import models.PasskeyAuthenticator
import play.api.Logging

import java.security.cert.{CertificateFactory, X509Certificate}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import scala.util.{Try, Using}

/** Resolves human-readable metadata (description and icon) for a passkey
  * authenticator from its AAGUID.
  *
  * Authenticators certified by the FIDO Alliance are looked up via the FIDO
  * Metadata Service (MDS), whose signed metadata BLOB is downloaded and cached
  * in memory at runtime by webauthn4j's [[FidoMDS3MetadataBLOBProvider]]. Many
  * common platform authenticators (e.g. iCloud Keychain, Google Password
  * Manager) are not published to the FIDO MDS, so a committed community
  * database is used as a fallback.
  *
  * MDS results take precedence over the community fallback. If the MDS lookup
  * fails (e.g. the service is unreachable), the lookup degrades gracefully to
  * the community fallback and then to `None`.
  *
  * @param communityResourcePath
  *   Classpath resource of the community authenticator database.
  * @param trustAnchorResourcePath
  *   Classpath resource of the FIDO MDS root CA certificate (PEM) used to
  *   verify the signature of the downloaded metadata BLOB.
  */
class PasskeyMetadataService(
    communityResourcePath: String,
    trustAnchorResourcePath: String
)(using ExecutionContext)
    extends Logging {

  private val communityMetadata: Map[AAGUID, PasskeyAuthenticator] =
    PasskeyAuthenticator.fromResource(communityResourcePath)

  private val mdsProvider: Option[FidoMDS3MetadataBLOBProvider] =
    loadTrustAnchor(trustAnchorResourcePath) match {
      case Right(trustAnchor) =>
        val provider =
          new FidoMDS3MetadataBLOBProvider(new ObjectConverter(), trustAnchor)
        // Revocation checking requires additional network access (OCSP/CRL)
        // and is unnecessary for sourcing cosmetic authenticator metadata.
        provider.setRevocationCheckEnabled(false)
        Some(provider)
      case Left(e) =>
        logger.error(
          "Failed to load FIDO MDS trust anchor; " +
            "passkey authenticator metadata will use the community fallback only",
          e
        )
        None
    }

  private val mdsRepository
      : Option[MetadataBLOBBasedMetadataStatementRepository] =
    mdsProvider.map { provider =>
      val repository =
        new MetadataBLOBBasedMetadataStatementRepository(provider)
      // Include authenticators that are present in the BLOB but not FIDO
      // certified, so that their descriptions are still available.
      repository.setNotFidoCertifiedAllowed(true)
      repository
    }

  // Warm the in-memory MDS cache in the background so the first user-facing
  // request doesn't pay the cost of downloading and parsing the metadata BLOB.
  warmCache()

  /** Resolves authenticator metadata for the given AAGUID, preferring FIDO MDS
    * data and falling back to the community database.
    */
  def find(aaguid: AAGUID): Option[PasskeyAuthenticator] =
    findInMds(aaguid).orElse(communityMetadata.get(aaguid))

  private def findInMds(aaguid: AAGUID): Option[PasskeyAuthenticator] =
    mdsRepository.flatMap { repository =>
      Try(repository.find(aaguid).asScala.headOption.map(toAuthenticator))
        .recover { case NonFatal(e) =>
          logger.warn(s"FIDO MDS lookup failed for AAGUID $aaguid", e)
          None
        }
        .toOption
        .flatten
    }

  private def toAuthenticator(
      statement: MetadataStatement
  ): PasskeyAuthenticator =
    PasskeyAuthenticator(statement.getDescription, Option(statement.getIcon))

  private def warmCache(): Unit =
    mdsProvider.foreach { provider =>
      Future {
        provider.provide()
        logger.info("Warmed FIDO MDS metadata cache")
      }.recover { case NonFatal(e) =>
        logger.warn(
          "Failed to warm FIDO MDS metadata cache; " +
            "it will be populated on first use",
          e
        )
      }
    }

  private def loadTrustAnchor(
      resourcePath: String
  ): Either[Throwable, X509Certificate] =
    Using(getClass.getClassLoader.getResourceAsStream(resourcePath)) { stream =>
      CertificateFactory
        .getInstance("X.509")
        .generateCertificate(stream)
        .asInstanceOf[X509Certificate]
    }.toEither
}
