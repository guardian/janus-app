package aws

import com.gu.googleauth.UserIdentity
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.util.Base64UrlUtil
import models.JanusException
import play.api.http.Status.INTERNAL_SERVER_ERROR
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model._

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

object PasskeyDB {

  private[aws] val tableName = "Passkeys"

  private val credentialDataConverter = new AttestedCredentialDataConverter(
    new ObjectConverter()
  )

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#credentialrecord-serialization-and-deserialization]]
    * for serialisation details.
    */
  def toDynamoItem(
      user: UserIdentity,
      credentialRecord: CredentialRecord
  ): Map[String, AttributeValue] =
    Map(
      "username" -> AttributeValue.fromS(user.username),
      // TODO: does this correspond to the type of passkey? What happens if you try to register the same passkey twice?
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
      )
    )

  def insert(
      user: UserIdentity,
      credentialRecord: CredentialRecord
  )(implicit dynamoDB: DynamoDbClient): Try[Unit] = Try {
    val item = toDynamoItem(user, credentialRecord)
    val request =
      PutItemRequest.builder().tableName(tableName).item(item.asJava).build()
    dynamoDB.putItem(request)
  }.map(_ => ())
    .recoverWith(exception =>
      Failure(
        JanusException(
          userMessage = "Failed to store credential",
          engineerMessage =
            s"Failed to store credential for user ${user.username}: ${exception.getMessage}",
          httpCode = INTERNAL_SERVER_ERROR,
          causedBy = Some(exception)
        )
      )
    )
}
