package aws

import com.gu.googleauth.UserIdentity
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.util.Base64UrlUtil
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model._

import scala.jdk.CollectionConverters._
import scala.util.Try

object PasskeyDB {

  private[aws] val tableName = "Passkeys"

  private val credentialDataConverter = new AttestedCredentialDataConverter(
    new ObjectConverter()
  )

  case class UserCredentialRecord(
      user: UserIdentity,
      credentialRecord: CredentialRecord
  )

  object UserCredentialRecord {

    /** See
      * [[https://webauthn4j.github.io/webauthn4j/en/#credentialrecord-serialization-and-deserialization]]
      * for serialisation details.
      */
    def toDynamoItem(
        userCredentialRecord: UserCredentialRecord
    ): Map[String, AttributeValue] = {
      Map(
        "userName" -> AttributeValue.fromS(userCredentialRecord.user.username),
        // TODO: does this correspond to the type of passkey? What happens if you try to register the same passkey twice?
        "credentialId" -> AttributeValue.fromS(
          Base64UrlUtil.encodeToString(
            userCredentialRecord.credentialRecord.getAttestedCredentialData.getCredentialId
          )
        ),
        "credential" -> AttributeValue.fromS(
          Base64UrlUtil.encodeToString(
            credentialDataConverter.convert(
              userCredentialRecord.credentialRecord.getAttestedCredentialData
            )
          )
        )
      )
    }
  }

  def insert(
      userCredentialRecord: UserCredentialRecord
  )(implicit dynamoDB: DynamoDbClient): Try[Unit] = {
    val item = UserCredentialRecord.toDynamoItem(userCredentialRecord).asJava
    val request =
      PutItemRequest.builder().tableName(tableName).item(item).build()
    Try(dynamoDB.putItem(request))
  }
}
