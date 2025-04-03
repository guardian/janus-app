package aws

import com.gu.googleauth.UserIdentity
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.util.Base64UrlUtil
import models.AwsCallException
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model._

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

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
        "username" -> AttributeValue.fromS(userCredentialRecord.user.username),
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
  )(implicit dynamoDB: DynamoDbClient): Either[AwsCallException, Unit] = Try {
    val item = UserCredentialRecord.toDynamoItem(userCredentialRecord)
    val request =
      PutItemRequest.builder().tableName(tableName).item(item.asJava).build()
    dynamoDB.putItem(request)
  } match {
    case Failure(exception) =>
      Left(
        AwsCallException(
          "Failed to store credential",
          s"Failed to store credential for user ${userCredentialRecord.user.username}: ${exception.getMessage}",
          exception
        )
      )
    case Success(_) => Right(())
  }
}
