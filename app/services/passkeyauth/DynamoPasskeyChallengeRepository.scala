package services.passkeyauth

import aws.PasskeyChallengeDB
import aws.PasskeyChallengeDB.UserChallenge
import com.gu.playpasskeyauth.models.UserId
import com.gu.playpasskeyauth.services.{
  ChallengeType,
  PasskeyChallengeRepository,
  PasskeyError,
  PasskeyException
}
import com.webauthn4j.data.client.challenge.Challenge
import models.PasskeyFlow
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import java.time.{Instant, Clock}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class DynamoPasskeyChallengeRepository(clock: Clock = Clock.systemUTC())(using
    dynamoDb: DynamoDbClient,
    ec: ExecutionContext
) extends PasskeyChallengeRepository {

  import UserIdentityConversions.toUserIdentity

  override def load(
      userId: UserId,
      challengeType: ChallengeType
  ): Future[Challenge] =
    Future.fromTry {
      val user = toUserIdentity(userId)
      for {
        response <- PasskeyChallengeDB.loadChallenge(
          user,
          toFlow(challengeType)
        )
        _ <-
          if response.hasItem then Success(())
          else Failure(PasskeyException(PasskeyError.ChallengeExpired))
        _ <- {
          val expiresAt =
            Instant.ofEpochSecond(response.item().get("expiresAt").n().toLong)
          if expiresAt.isAfter(clock.instant()) then Success(())
          else Failure(PasskeyException(PasskeyError.ChallengeExpired))
        }
        challenge <- PasskeyChallengeDB.extractChallenge(response, user)
      } yield challenge
    }

  override def insert(
      userId: UserId,
      challenge: Challenge,
      expiresAt: Instant,
      challengeType: ChallengeType
  ): Future[Unit] =
    Future.fromTry {
      PasskeyChallengeDB.insert(
        UserChallenge(
          user = toUserIdentity(userId),
          flow = toFlow(challengeType),
          challenge = challenge,
          expiresAt = expiresAt
        )
      )
    }

  override def delete(
      userId: UserId,
      challengeType: ChallengeType
  ): Future[Unit] =
    Future.fromTry {
      PasskeyChallengeDB.delete(toUserIdentity(userId), toFlow(challengeType))
    }

  private def toFlow(challengeType: ChallengeType): PasskeyFlow =
    challengeType match {
      case ChallengeType.Registration   => PasskeyFlow.Registration
      case ChallengeType.Authentication => PasskeyFlow.Authentication
    }

}
