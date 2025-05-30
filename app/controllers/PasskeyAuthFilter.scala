package controllers

import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.googleauth.UserIdentity
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.data.AuthenticationData
import com.webauthn4j.data.client.challenge.Challenge
import logic.Passkey
import models.JanusException
import models.JanusException.throwableWrites
import org.apache.pekko.actor.ActorSystem
import play.api.{Logger, Logging}
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.Json.toJson
import play.api.mvc.Results.Status
import play.api.mvc.{ActionFilter, AnyContentAsFormUrlEncoded, Result}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object PasskeyAuthFilter {

  def shouldAuthenticateWithPasskey[A](
      request: UserIdentityRequest[A],
      passkeysEnabled: Boolean,
      enablingCookieName: String
  ): Boolean = {
    val enablingCookieIsPresent =
      request.cookies.get(enablingCookieName).isDefined
    passkeysEnabled && enablingCookieIsPresent
  }

  def performPasskeyAuthentication[A](
      request: UserIdentityRequest[A],
      host: String
  )(implicit ec: ExecutionContext, dynamoDb: DynamoDbClient): Try[Unit] =
    for {
      challenge <- loadAndExtractChallenge(request.user)
      authData <- extractAuthenticationData(request)
      credential <- loadAndExtractCredential(
        request.user,
        authData.getCredentialId
      )
      verifiedAuthData <- verifyAuthentication(
        request.user,
        challenge,
        authData,
        credential,
        host
      )
      _ <- cleanupAndUpdateCredential(request.user, verifiedAuthData)
    } yield ()

  def loadAndExtractChallenge(
      user: UserIdentity
  )(implicit ec: ExecutionContext, dynamoDb: DynamoDbClient): Try[Challenge] =
    for {
      challengeResponse <- PasskeyChallengeDB.loadChallenge(user)
      challenge <- PasskeyChallengeDB.extractChallenge(challengeResponse, user)
    } yield challenge

  def loadAndExtractCredential(user: UserIdentity, credentialId: Array[Byte])(
      implicit
      ec: ExecutionContext,
      dynamoDb: DynamoDbClient
  ): Try[CredentialRecord] =
    for {
      credentialResponse <- PasskeyDB.loadCredential(user, credentialId)
      credential <- PasskeyDB.extractCredential(credentialResponse, user)
    } yield credential

  def verifyAuthentication(
      user: UserIdentity,
      challenge: Challenge,
      authData: AuthenticationData,
      credential: CredentialRecord,
      host: String
  ): Try[AuthenticationData] =
    Passkey.verifiedAuthentication(host, user, challenge, authData, credential)

  def cleanupAndUpdateCredential(
      user: UserIdentity,
      verifiedAuthData: AuthenticationData
  )(implicit ec: ExecutionContext, dynamoDb: DynamoDbClient): Try[Unit] =
    for {
      _ <- PasskeyChallengeDB.delete(user)
      _ <- PasskeyDB.updateCounter(user, verifiedAuthData)
      _ <- PasskeyDB.updateLastUsedTime(user, verifiedAuthData)
    } yield ()

  def extractAuthenticationData[A](
      request: UserIdentityRequest[A]
  ): Try[AuthenticationData] =
    for {
      credentialsBody <- extractCredentialsFromRequest(request)
      authData <- Passkey.parsedAuthentication(request.user, credentialsBody)
    } yield authData

  def extractCredentialsFromRequest[A](
      request: UserIdentityRequest[A]
  ): Try[String] =
    request.body match {
      case AnyContentAsFormUrlEncoded(data) =>
        data
          .get("credentials")
          .flatMap(_.headOption)
          .toRight(
            JanusException.missingFieldInRequest(request.user, "credentials")
          )
          .toTry
      case _ =>
        Failure(
          JanusException.missingFieldInRequest(request.user, "credentials")
        )
    }

  def handleApiResponse(
      operation: => Try[Unit],
      username: String
  )(implicit logger: Logger): Option[Result] =
    operation match {
      case Failure(err: JanusException) =>
        logger.error(err.engineerMessage, err.causedBy.orNull)
        Some(Status(err.httpCode)(toJson(err)))
      case Failure(err) =>
        logger.error(err.getMessage, err)
        Some(Status(INTERNAL_SERVER_ERROR)(toJson(err)))
      case Success(_) =>
        logger.info(
          s"Authenticated passkey for user $username"
        )
        None
    }
}

/** Performs passkey authentication and only allows an action to continue if
  * authentication is successful.
  *
  * See
  * [[https://webauthn4j.github.io/webauthn4j/en/#webauthn-assertion-verification-and-post-processing]].
  */
class PasskeyAuthFilter @Inject() (
    host: String,
    passkeysEnabled: Boolean,
    enablingCookieName: String,
    actorSystem: ActorSystem
)(implicit
    dynamoDb: DynamoDbClient
) extends ActionFilter[UserIdentityRequest]
    with Logging {

  // Use a custom dispatcher for blocking I/O operations (DynamoDB calls)
  implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("passkey.auth-dispatcher")

  def filter[A](request: UserIdentityRequest[A]): Future[Option[Result]] = {
    if (
      PasskeyAuthFilter.shouldAuthenticateWithPasskey(
        request,
        passkeysEnabled,
        enablingCookieName
      )
    ) {
      logger.info(
        s"Authenticating passkey for user ${request.user.username} ..."
      )
      authenticatePasskey(request)
    } else {
      Future.successful(None)
    }
  }

  private def authenticatePasskey[A](
      request: UserIdentityRequest[A]
  ): Future[Option[Result]] =
    Future(
      PasskeyAuthFilter.handleApiResponse(
        PasskeyAuthFilter.performPasskeyAuthentication(request, host),
        request.user.username
      )(logger)
    )
}
