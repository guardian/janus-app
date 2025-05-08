package controllers

import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.googleauth.UserIdentity
import logic.Passkey
import models.JanusException
import models.JanusException.throwableWrites
import play.api.Logging
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.Json.toJson
import play.api.mvc.Results.Status
import play.api.mvc._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Performs passkey authentication and only allows an action to continue if
  * authentication is successful.
  *
  * See
  * [[https://webauthn4j.github.io/webauthn4j/en/#webauthn-assertion-verification-and-post-processing]].
  */
class PasskeyAuthFilter(host: String)(implicit
    dynamoDb: DynamoDbClient,
    ec: ExecutionContext
) extends ActionFilter[UserIdentityRequest]
    with Logging {

  // TODO: Consider a separate EC for passkey processing
  def executionContext: ExecutionContext = ec

  def filter[A](request: UserIdentityRequest[A]): Future[Option[Result]] =
    Future(
      apiResponse(
        request.body match {
          case AnyContentAsFormUrlEncoded(formData) =>
            filterByFormData(request.user, formData)
          case body: AnyContent =>
            Failure(
              JanusException.missingFormInRequestBody(
                request.user,
                body.getClass.getSimpleName
              )
            )
        }
      )
    )

  private def filterByFormData(
      user: UserIdentity,
      formData: Map[String, Seq[String]]
  ): Try[Unit] =
    for {
      jsonCredentials <- formData
        .get("credentials")
        .flatMap(_.headOption)
        .toRight(JanusException.missingFieldInRequest(user, "credentials"))
        .toTry
      _ <- authenticateWithPasskey(user, jsonCredentials)
    } yield ()

  private def authenticateWithPasskey(
      user: UserIdentity,
      jsonCredentials: String
  ): Try[Unit] =
    for {
      challengeLoad <- PasskeyChallengeDB.loadChallenge(user)
      challenge <- PasskeyChallengeDB.extractChallenge(challengeLoad, user)
      authData <- Passkey.parsedAuthentication(user, jsonCredentials)
      credentialLoad <- PasskeyDB.loadCredential(
        user,
        authData.getCredentialId
      )
      credential <- PasskeyDB.extractCredential(credentialLoad, user)
      verifiedAuthData <- Passkey.verifiedAuthentication(
        host,
        user,
        challenge,
        authData,
        credential
      )
      _ <- PasskeyChallengeDB.delete(user)
      _ <- PasskeyDB.updateCounter(user, verifiedAuthData)
      _ <- PasskeyDB.updateLastUsedTime(user, verifiedAuthData)
      _ = logger.info(s"Authenticated passkey for user ${user.username}")
    } yield ()

  private def apiResponse(auth: => Try[Unit]): Option[Result] =
    auth match {
      case Failure(err: JanusException) =>
        logger.error(err.engineerMessage, err.causedBy.orNull)
        Some(Status(err.httpCode)(toJson(err)))
      case Failure(err) =>
        logger.error(err.getMessage, err)
        Some(Status(INTERNAL_SERVER_ERROR)(toJson(err)))
      case Success(_) => None
    }
}
