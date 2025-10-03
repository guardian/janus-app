package passkey

import aws.PasskeyDB
import com.gu.googleauth.UserIdentity
import com.gu.playpasskeyauth.services.PasskeyRepository
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.data.AuthenticationData
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.Future
import scala.util.Failure

class Repository(using DynamoDbClient) extends PasskeyRepository {

  override def loadCredentialRecord(
      userId: String,
      passkeyId: Array[Byte]
  ): Future[CredentialRecord] = {
    val userIdentity = toUserIdentity(userId)
    Future.fromTry(
      PasskeyDB.loadCredential(userIdentity, passkeyId).flatMap { response =>
        if (response.hasItem) {
          PasskeyDB.extractCredential(response, userIdentity)
        } else
          Failure(
            RuntimeException(
              s"Failed to load credential record for user: $userId"
            )
          )
      }
    )
  }

  override def loadPasskeyIds(userId: String): Future[List[String]] = {
    Future.fromTry(
      PasskeyDB.loadCredentials(toUserIdentity(userId)).map { response =>
        PasskeyDB.extractMetadata(response).map(_.id).toList
      }
    )
  }

  def insertCredentialRecord(
      userId: String,
      passkeyName: String,
      credentialRecord: CredentialRecord
  ): Future[Unit] = {
    Future.fromTry(PasskeyDB.insert(toUserIdentity(userId), credentialRecord, passkeyName))
  }

  override def updateAuthenticationCounter(
      userId: String,
      authData: AuthenticationData
  ): Future[Unit] = {
    Future.fromTry(PasskeyDB.updateCounter(toUserIdentity(userId), authData))
  }

  override def updateLastUsedTime(
      userId: String,
      authData: AuthenticationData
  ): Future[Unit] = {
    Future.fromTry(PasskeyDB.updateLastUsedTime(toUserIdentity(userId), authData))
  }

  private def toUserIdentity(userId: String) =
     UserIdentity(
      sub = "",
      email = userId,
      firstName = "",
      lastName = "",
      exp = 0L,
      avatarUrl = None
    )
}
