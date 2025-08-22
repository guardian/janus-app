package passkey

import aws.PasskeyDB
import com.gu.googleauth.UserIdentity
import com.gu.playpasskeyauth.services.PasskeyRepository
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.data.AuthenticationData
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.Future
import scala.util.Success

class Repository(using DynamoDbClient) extends PasskeyRepository {

  override def loadCredentialRecord(
      userId: String,
      passkeyId: Array[Byte]
  ): Future[Option[CredentialRecord]] = {
    val userIdentity = UserIdentity(
      sub = userId,
      email = userId,
      firstName = userId,
      lastName = "",
      exp = 0L,
      avatarUrl = None
    )
    Future.fromTry(
      PasskeyDB.loadCredential(userIdentity, passkeyId).flatMap { response =>
        if (response.hasItem) {
          PasskeyDB.extractCredential(response, userIdentity).map(Some(_))
        } else {
          Success(None)
        }
      }
    )
  }

  override def loadPasskeyIds(userId: String): Future[List[String]] = {
    val userIdentity = UserIdentity(
      sub = userId,
      email = userId,
      firstName = userId,
      lastName = "",
      exp = 0L,
      avatarUrl = None
    )
    Future.fromTry(
      PasskeyDB.loadCredentials(userIdentity).map { response =>
        PasskeyDB.extractMetadata(response).map(_.id).toList
      }
    )
  }

  override def updateAuthenticationCounter(
      userId: String,
      authData: AuthenticationData
  ): Future[Unit] = {
    val userIdentity = UserIdentity(
      sub = userId,
      email = userId,
      firstName = userId,
      lastName = "",
      exp = 0L,
      avatarUrl = None
    )
    Future.fromTry(PasskeyDB.updateCounter(userIdentity, authData))
  }

  override def updateLastUsedTime(
      userId: String,
      authData: AuthenticationData
  ): Future[Unit] = {
    val userIdentity = UserIdentity(
      sub = userId,
      email = userId,
      firstName = userId,
      lastName = "",
      exp = 0L,
      avatarUrl = None
    )
    Future.fromTry(PasskeyDB.updateLastUsedTime(userIdentity, authData))
  }
}
