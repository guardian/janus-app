package aws

import cats.implicits.catsSyntaxMonadError
import com.gu.googleauth.UserIdentity
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.{CredentialRecord, CredentialRecordImpl}
import com.webauthn4j.data.AuthenticationData
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement
import com.webauthn4j.data.extension.authenticator.{
  AuthenticationExtensionsAuthenticatorOutputs,
  RegistrationExtensionAuthenticatorOutput
}
import com.webauthn4j.util.Base64UrlUtil
import models.{JanusException, PasskeyMetadata}
import play.api.http.Status.INTERNAL_SERVER_ERROR
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

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
      passkeyName: String,
      registrationTime: Instant
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
      "passkeyName" -> AttributeValue.fromS(passkeyName),
      "registrationTime" -> AttributeValue.fromS(registrationTime.toString),
      "aaguid" -> AttributeValue.fromS(
        credentialRecord.getAttestedCredentialData.getAaguid.toString
      )
    )

  def insert(
      user: UserIdentity,
      credentialRecord: CredentialRecord,
      passkeyName: String,
      registrationTime: Instant = Instant.now()
  )(implicit dynamoDB: DynamoDbClient): Try[Unit] = Try {
    val item =
      toDynamoItem(user, credentialRecord, passkeyName, registrationTime)
    val request =
      PutItemRequest.builder().tableName(tableName).item(item.asJava).build()
    dynamoDB.putItem(request)
    ()
  }.adaptError(err => JanusException.failedToCreateDbItem(user, tableName, err))

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
    }.adaptError(err => JanusException.failedToLoadDbItem(user, tableName, err))
  }

  def extractCredential(
      response: GetItemResponse,
      user: UserIdentity
  ): Try[CredentialRecord] = {
    if (response.hasItem) {
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
      Success(
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
      )
    } else
      Failure(JanusException.missingItemInDb(user, tableName))
  }

  def loadCredentials(
      user: UserIdentity
  )(implicit dynamoDB: DynamoDbClient): Try[QueryResponse] =
    Try {
      val expressionValues = Map(
        ":username" -> AttributeValue.fromS(user.username)
      )
      val request = QueryRequest
        .builder()
        .tableName(tableName)
        .keyConditionExpression("username = :username")
        .expressionAttributeValues(expressionValues.asJava)
        .build()
      dynamoDB.query(request)
    }.adaptError(err => JanusException.failedToLoadDbItem(user, tableName, err))

  def extractMetadata(response: QueryResponse): Seq[PasskeyMetadata] =
    if (response.hasItems && !response.items().isEmpty) {
      response
        .items()
        .asScala
        .map(attribs =>
          PasskeyMetadata(
            id = attribs.get("credentialId").s(),
            name = attribs.get("passkeyName").s(),
            registrationTime =
              Instant.parse(attribs.get("registrationTime").s()),
            aaguid = new AAGUID(attribs.get("aaguid").s()),
            lastUsedTime =
              if (attribs.containsKey("lastUsedTime"))
                Some(Instant.parse(attribs.get("lastUsedTime").s()))
              else None,
            authenticator = None
          )
        )
        .toSeq
    } else Nil

  /** The device hosting the passkey keeps a count of how many times the passkey
    * has been requested. As part of the verification process, this count is
    * compared with the request count stored in the DB.
    *
    * See https://www.w3.org/TR/webauthn-1/#sign-counter
    */
  def updateCounter(user: UserIdentity, authData: AuthenticationData)(implicit
      dynamoDB: DynamoDbClient
  ): Try[Unit] =
    updateAttribute(
      user,
      authData,
      "authCounter",
      AttributeValue.fromN(
        String.valueOf(authData.getAuthenticatorData.getSignCount)
      )
    )

  def updateLastUsedTime(
      user: UserIdentity,
      authData: AuthenticationData,
      lastUsedTime: Instant = Instant.now()
  )(implicit dynamoDB: DynamoDbClient): Try[Unit] =
    updateAttribute(
      user,
      authData,
      "lastUsedTime",
      AttributeValue.fromS(lastUsedTime.toString)
    )

  private def updateAttribute(
      user: UserIdentity,
      authData: AuthenticationData,
      attribName: String,
      attribValue: AttributeValue
  )(implicit
      dynamoDB: DynamoDbClient
  ): Try[Unit] = Try {
    val key = Map(
      "username" -> AttributeValue.fromS(user.username),
      "credentialId" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(authData.getCredentialId)
      )
    )
    val update = Map(s":${attribName}Value" -> attribValue)
    val request = UpdateItemRequest.builder
      .tableName(tableName)
      .key(key.asJava)
      .updateExpression(s"SET $attribName = :${attribName}Value")
      .expressionAttributeValues(update.asJava)
      .build()
    dynamoDB.updateItem(request)
    ()
  }.adaptError(err =>
    JanusException.failedToUpdateDbItem(user, tableName, attribName, err)
  )

  def deleteById(
      user: UserIdentity,
      credentialId: String
  )(implicit dynamoDB: DynamoDbClient): Try[Unit] = Try {
    val key = Map(
      "username" -> AttributeValue.fromS(user.username),
      "credentialId" -> AttributeValue.fromS(credentialId)
    )
    val request = DeleteItemRequest
      .builder()
      .tableName(tableName)
      .key(key.asJava)
      .build()
    dynamoDB.deleteItem(request)
    ()
  }.adaptError(err => JanusException.failedToDeleteDbItem(user, tableName, err))

  /** Checks if a passkey name already exists for the given user. */
  def passkeyNameExists(
      user: UserIdentity,
      passkeyName: String
  )(implicit dynamoDB: DynamoDbClient): Try[Boolean] = Try {
    val expressionValues = Map(
      ":username" -> AttributeValue.fromS(user.username),
      ":passkeyName" -> AttributeValue.fromS(passkeyName)
    )
    val request = QueryRequest
      .builder()
      .tableName(tableName)
      .keyConditionExpression("username = :username")
      .filterExpression("passkeyName = :passkeyName")
      .expressionAttributeValues(expressionValues.asJava)
      .build()

    val response = dynamoDB.query(request)
    response.hasItems && !response.items().isEmpty
  }.adaptError(err => JanusException.failedToLoadDbItem(user, tableName, err))
}
