package aws

import com.gu.googleauth.UserIdentity
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.{CredentialRecord, CredentialRecordImpl}
import com.webauthn4j.data.AuthenticationData
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement
import com.webauthn4j.data.extension.authenticator.{
  AuthenticationExtensionsAuthenticatorOutputs,
  RegistrationExtensionAuthenticatorOutput
}
import com.webauthn4j.util.Base64UrlUtil
import models.JanusException
import play.api.http.Status.INTERNAL_SERVER_ERROR
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model._

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

object PasskeyDB {

  private[aws] val tableName = "Passkeys"

  private val objConverter = new ObjectConverter()

  private val credentialDataConverter = new AttestedCredentialDataConverter(
    objConverter
  )

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#credentialrecord-serialization-and-deserialization]]
    * for serialisation details.
    */
  def toDynamoItem(
      user: UserIdentity,
      credentialRecord: CredentialRecord,
      passkeyName: String
  ): Map[String, AttributeValue] =
    Map(
      "username" -> AttributeValue.fromS(user.username),
      "credentialId" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(
          credentialRecord.getAttestedCredentialData.getCredentialId
        )
      ),
      "credential" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(
          credentialDataConverter.convert(
            credentialRecord.getAttestedCredentialData
          )
        )
      ),
      "attestationStatement" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(
          objConverter.getCborConverter.writeValueAsBytes(
            credentialRecord.getAttestationStatement
          )
        )
      ),
      "authenticatorExtensions" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(
          objConverter.getCborConverter.writeValueAsBytes(
            credentialRecord.getAuthenticatorExtensions
          )
        )
      ),
      // see https://www.w3.org/TR/webauthn-1/#sign-counter
      "authCounter" -> AttributeValue.fromN("0"),
      "passkeyName" -> AttributeValue.fromS(passkeyName)
    )

  def insert(
      user: UserIdentity,
      credentialRecord: CredentialRecord,
      passkeyName: String
  )(implicit dynamoDB: DynamoDbClient): Try[Unit] = Try {
    val item = toDynamoItem(user, credentialRecord, passkeyName)
    val request =
      PutItemRequest.builder().tableName(tableName).item(item.asJava).build()
    dynamoDB.putItem(request)
    ()
  }.recoverWith(exception =>
    Failure(
      JanusException(
        userMessage = "Failed to store passkey",
        engineerMessage =
          s"Failed to store credential for user ${user.username}: ${exception.getMessage}",
        httpCode = INTERNAL_SERVER_ERROR,
        causedBy = Some(exception)
      )
    )
  )

  def loadCredential(
      user: UserIdentity,
      credentialId: Array[Byte]
  )(implicit dynamoDB: DynamoDbClient): Try[GetItemResponse] = {
    Try {
      val key = Map(
        "username" -> AttributeValue.fromS(user.username),
        "credentialId" -> AttributeValue.fromS(
          Base64UrlUtil.encodeToString(credentialId)
        )
      )
      val request =
        GetItemRequest.builder().tableName(tableName).key(key.asJava).build()
      dynamoDB.getItem(request)
    }.recoverWith(err =>
      Failure(
        JanusException(
          userMessage = "Failed to find registered passkey",
          engineerMessage =
            s"Failed to load credential for user ${user.username}: ${err.getMessage}",
          httpCode = INTERNAL_SERVER_ERROR,
          causedBy = Some(err)
        )
      )
    )
  }

  def extractCredential(
      response: GetItemResponse,
      user: UserIdentity
  ): Try[CredentialRecord] = {
    if (response.hasItem) {
      Try {
        val item = response.item()
        val attestationStmt = objConverter.getCborConverter.readValue(
          Base64UrlUtil.decode(item.get("attestationStatement").s()),
          classOf[NoneAttestationStatement]
        )
        val counter = item.get("authCounter").n().toLong
        val credentialData = credentialDataConverter.convert(
          Base64UrlUtil.decode(item.get("credential").s())
        )
        val authExts = objConverter.getCborConverter.readValue(
          Base64UrlUtil.decode(item.get("authenticatorExtensions").s()),
          classOf[AuthenticationExtensionsAuthenticatorOutputs[
            RegistrationExtensionAuthenticatorOutput
          ]]
        )
        new CredentialRecordImpl(
          attestationStmt,
          null,
          null,
          null,
          counter,
          credentialData,
          authExts,
          null,
          null,
          null
        )
      }.recoverWith(err =>
        Failure(
          JanusException(
            userMessage = "Invalid registered passkey",
            engineerMessage =
              s"Failed to extract credential data for user ${user.username}: ${err.getMessage}",
            httpCode = INTERNAL_SERVER_ERROR,
            causedBy = Some(err)
          )
        )
      )
    } else {
      Failure(
        JanusException(
          userMessage = "Failed to find registered passkey",
          engineerMessage =
            s"Credential data not found for user ${user.username}: GetItem response: $response",
          httpCode = INTERNAL_SERVER_ERROR,
          causedBy = None
        )
      )
    }
  }

  /** The device hosting the passkey keeps a count of how many times the passkey
    * has been requested. As part of the verification process, this count is
    * compared with the request count stored in the DB.
    *
    * See https://www.w3.org/TR/webauthn-1/#sign-counter
    */
  def updateCounter(user: UserIdentity, authData: AuthenticationData)(implicit
      dynamoDB: DynamoDbClient
  ): Try[Unit] = Try {
    val key = Map(
      "username" -> AttributeValue.fromS(user.username),
      "credentialId" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(authData.getCredentialId)
      )
    )
    val update =
      Map(
        ":counterValue" -> AttributeValue.fromN(
          String.valueOf(authData.getAuthenticatorData.getSignCount)
        )
      )
    val request = UpdateItemRequest.builder
      .tableName(tableName)
      .key(key.asJava)
      .updateExpression("SET authCounter = :counterValue")
      .expressionAttributeValues(update.asJava)
      .build()
    dynamoDB.updateItem(request)
    ()
  }.recoverWith(err =>
    Failure(
      JanusException(
        userMessage = "Failed to update authentication counter",
        engineerMessage =
          s"Failed to update authentication counter for user ${user.username}: ${err.getMessage}",
        httpCode = INTERNAL_SERVER_ERROR,
        causedBy = Some(err)
      )
    )
  )

  def fetchByUser(user: UserIdentity)(implicit dynamoDB: DynamoDbClient): Try[GetItemResponse] = {
    Try {
      val key = Map("username" -> AttributeValue.fromS(user.username))
      val request =
        GetItemRequest.builder().tableName(tableName).key(key.asJava).build()
      dynamoDB.getItem(request)
    }.recoverWith(err =>
      Failure(
        JanusException(
          userMessage = "Failed to load user passkeys",
          engineerMessage =
            s"Failed to load user passkeys for ${user.username}: ${err.getMessage}",
          httpCode = INTERNAL_SERVER_ERROR,
          causedBy = Some(err)
        )
      )
    )
  }

}

def extractPasskeys(response: GetRecordsResponse): Try[Seq[String]] = {
  if (response.hasItem) {
    Try {
      val item = response.
      val passkeys =
        Base64UrlUtil.decode(item.get("passkey").s().getBytes(UTF_8))
      new DefaultChallenge(challenge)
    }
  } else {
    Failure(
      JanusException(
        userMessage = "Challenge not found",
        engineerMessage = s"Challenge not found for user ${user.username}",
        httpCode = INTERNAL_SERVER_ERROR,
        causedBy = None
      )
    )
  }
}