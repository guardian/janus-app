package passkey

import aws.PasskeyChallengeDB
import com.gu.googleauth.UserIdentity
import com.gu.playpasskeyauth.services.PasskeyChallengeRepository
import com.webauthn4j.data.client.challenge.Challenge
import models.PasskeyFlow
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.Future
import scala.util.Failure

class ChallengeRepository(using DynamoDbClient)
    extends PasskeyChallengeRepository {

  def loadRegistrationChallenge(userId: String): Future[Challenge] = {
    val userIdentity = toUserIdentity(userId)
    Future.fromTry(
      PasskeyChallengeDB
        .loadChallenge(userIdentity, PasskeyFlow.Registration)
        .flatMap { response =>
          if (response.hasItem) {
            PasskeyChallengeDB
              .extractChallenge(response, userIdentity)
          } else {
            Failure(
              RuntimeException(
                s"Failed to load registration challenge for user: $userId"
              )
            )
          }
        }
    )
  }

  override def loadAuthenticationChallenge(
      userId: String
  ): Future[Challenge] = {
    val userIdentity = toUserIdentity(userId)
    Future.fromTry(
      PasskeyChallengeDB
        .loadChallenge(userIdentity, PasskeyFlow.Authentication)
        .flatMap { response =>
          if (response.hasItem) {
            PasskeyChallengeDB
              .extractChallenge(response, userIdentity)
          } else {
            Failure(
              RuntimeException(
                s"Failed to load authentication challenge for user: $userId"
              )
            )
          }
        }
    )
  }

  def insertRegistrationChallenge(
      userId: String,
      challenge: Challenge
  ): Future[Unit] = {
    Future.fromTry(
      PasskeyChallengeDB.insert(
        PasskeyChallengeDB.UserChallenge(
          toUserIdentity(userId),
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
    Future.fromTry(
      PasskeyChallengeDB.insert(
        PasskeyChallengeDB.UserChallenge(
          toUserIdentity(userId),
          PasskeyFlow.Authentication,
          challenge
        )
      )
    )
  }

  def deleteRegistrationChallenge(userId: String): Future[Unit] = {
    Future.fromTry(
      PasskeyChallengeDB.delete(toUserIdentity(userId), PasskeyFlow.Registration)
    )
  }

  override def deleteAuthenticationChallenge(userId: String): Future[Unit] = {
    Future.fromTry(
      PasskeyChallengeDB.delete(toUserIdentity(userId), PasskeyFlow.Authentication)
    )
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
