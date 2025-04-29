package controllers

import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.AuthAction.UserIdentityRequest
import io.circe.Json
import logic.Passkey
import models.JanusException
import play.api.Logging
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR}
import play.api.mvc.Results.Status
import play.api.mvc.{ActionFilter, AnyContentAsFormUrlEncoded, Result}
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
    Future.successful(
      apiResponse(
        for {
          challengeResponse <- PasskeyChallengeDB.loadChallenge(request.user)
          challenge <- PasskeyChallengeDB.extractChallenge(
            challengeResponse,
            request.user
          )
          authData <- extractAuthenticationData(request)
          credentialResponse <- PasskeyDB.loadCredential(
            request.user,
            authData.getCredentialId
          )
          credential <- PasskeyDB.extractCredential(
            credentialResponse,
            request.user
          )
          verifiedAuthData <- Passkey.verifiedAuthentication(
            host,
            challenge,
            authData,
            credential
          )
          _ <- PasskeyChallengeDB.delete(request.user)
          _ <- PasskeyDB.updateCounter(request.user, verifiedAuthData)
          _ = logger.info(
            s"Authenticated passkey for user ${request.user.username}"
          )
        } yield ()
      )
    )

  private def apiResponse(auth: => Try[Unit]): Option[Result] =
    auth match {
      case Failure(err: JanusException) =>
        logger.error(err.engineerMessage, err.causedBy.orNull)
        val json = Json.obj(
          "status" -> Json.fromString("error"),
          "message" -> Json.fromString(err.userMessage)
        )
        Some(Status(err.httpCode)(json.noSpaces))
      case Failure(err) =>
        logger.error(err.getMessage, err)
        val json = Json.obj(
          "status" -> Json.fromString("error"),
          "message" -> Json.fromString(err.getClass.getSimpleName)
        )
        Some(Status(INTERNAL_SERVER_ERROR)(json.noSpaces))
      case Success(_) => None
    }

  private def extractAuthenticationData[A](request: UserIdentityRequest[A]) =
    for {
      body <- (request.body match {
        case AnyContentAsFormUrlEncoded(data) =>
          data.get("credentials") match {
            case Some(credentials) => credentials.headOption
            case None     => None
          }
        case _ => None
      })
        .toRight(
          JanusException(
            userMessage = "Missing body in authentication request",
            engineerMessage =
              s"Missing body in authentication request for user ${request.user.username}",
            httpCode = BAD_REQUEST,
            causedBy = None
          )
        )
        .toTry
      authData <- Passkey.parsedAuthentication(body)
    } yield authData
}
