package passkey

import aws.PasskeyChallengeDB
import com.gu.googleauth.UserIdentity
import com.gu.playpasskeyauth.services.PasskeyChallengeRepository
import com.webauthn4j.data.client.challenge.Challenge
import models.PasskeyFlow
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.Future
import scala.util.Success

class ChallengeRepository(using DynamoDbClient)
    extends PasskeyChallengeRepository {

  def loadRegistrationChallenge(userId: String): Future[Option[Challenge]] = {
    val userIdentity = UserIdentity(
      sub = userId,
      email = userId,
      firstName = userId,
      lastName = "",
      exp = 0L,
      avatarUrl = None
    )
    Future.fromTry(
      PasskeyChallengeDB
        .loadChallenge(userIdentity, PasskeyFlow.Registration)
        .flatMap { response =>
          if (response.hasItem) {
            PasskeyChallengeDB
              .extractChallenge(response, userIdentity)
              .map(Some(_))
          } else {
            Success(None)
          }
        }
    )
  }

  override def loadAuthenticationChallenge(
      userId: String
  ): Future[Option[Challenge]] = {
    val userIdentity = UserIdentity(
      sub = userId,
      email = userId,
      firstName = userId,
      lastName = "",
      exp = 0L,
      avatarUrl = None
    )
    Future.fromTry(
      PasskeyChallengeDB
        .loadChallenge(userIdentity, PasskeyFlow.Authentication)
        .flatMap { response =>
          if (response.hasItem) {
            PasskeyChallengeDB
              .extractChallenge(response, userIdentity)
              .map(Some(_))
          } else {
            Success(None)
          }
        }
    )
  }

  def insertRegistrationChallenge(
      userId: String,
      challenge: Challenge
  ): Future[Unit] = {
    val userIdentity = UserIdentity(
      sub = userId,
      email = userId,
      firstName = userId,
      lastName = "",
      exp = 0L,
      avatarUrl = None
    )
    Future.fromTry(
      PasskeyChallengeDB.insert(
        PasskeyChallengeDB.UserChallenge(
          userIdentity,
          PasskeyFlow.Registration,
          challenge
        )
      )
    )
  }

  def insertAuthenticationChallenge(
      userId: String,
      challenge: Challenge
  ): Future[Unit] = {
    val userIdentity = UserIdentity(
      sub = userId,
      email = userId,
      firstName = userId,
      lastName = "",
      exp = 0L,
      avatarUrl = None
    )
    Future.fromTry(
      PasskeyChallengeDB.insert(
        PasskeyChallengeDB.UserChallenge(
          userIdentity,
          PasskeyFlow.Authentication,
          challenge
        )
      )
    )
  }

  def deleteRegistrationChallenge(userId: String): Future[Unit] = ???

  override def deleteAuthenticationChallenge(userId: String): Future[Unit] = {
    val userIdentity = UserIdentity(
      sub = userId,
      email = userId,
      firstName = userId,
      lastName = "",
      exp = 0L,
      avatarUrl = None
    )
    Future.fromTry(
      PasskeyChallengeDB.delete(userIdentity, PasskeyFlow.Authentication)
    )
  }
}
