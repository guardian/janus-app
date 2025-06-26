package controllers

import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.AuthAction.UserIdentityRequest
import logic.Passkey
import models.JanusException
import models.JanusException.throwableWrites
import models.PasskeyFlow.Authentication
import play.api.Logging
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.Json.toJson
import play.api.mvc.Results.Status
import play.api.mvc.{ActionFilter, AnyContentAsFormUrlEncoded, AnyContentAsText, Result}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Performs passkey authentication and only allows an action to continue if
  * authentication is successful.
  *
  * See
  * [[https://webauthn4j.github.io/webauthn4j/en/#webauthn-assertion-verification-and-post-processing]].
  */
class PasskeyAuthFilter(
    host: String,
    passkeysEnabled: Boolean,
    enablingCookieName: String
)(using
    dynamoDb: DynamoDbClient,
    ec: ExecutionContext
) extends ActionFilter[UserIdentityRequest]
    with Logging {

  // TODO: Consider a separate EC for passkey processing
  def executionContext: ExecutionContext = ec

  def filter[A](request: UserIdentityRequest[A]): Future[Option[Result]] = {
    val enablingCookieIsPresent =
      request.cookies.get(enablingCookieName).isDefined
    if (passkeysEnabled && enablingCookieIsPresent) {
      logger.info(
        s"Authenticating passkey for user ${request.user.username} ..."
      )
      doFilter(request)
    } else Future.successful(None)
  }

  private def doFilter[A](
      request: UserIdentityRequest[A]
  ): Future[Option[Result]] =
    Future(
      apiResponse(
        for {
          challengeResponse <- PasskeyChallengeDB.loadChallenge(
            request.user,
            Authentication
          )
          challenge <- PasskeyChallengeDB.extractChallenge(
            challengeResponse,
            request.user
          )
          authData <- extractAuthenticationData(request)
          credentialResponse <- PasskeyDB.loadCredential(
            request.user,
            authData.getCredentialId
          )
          _ <-
            if (credentialResponse.hasItem) Success(())
            else
              Failure(
                JanusException.authenticationFailure(
                  request.user,
                  new IllegalArgumentException("Invalid passkey credential")
                )
              )
          credential <- PasskeyDB.extractCredential(
            credentialResponse,
            request.user
          )
          verifiedAuthData <- Passkey.verifiedAuthentication(
            host,
            request.user,
            challenge,
            authData,
            credential
          )
          _ <- PasskeyChallengeDB.delete(request.user, Authentication)
          _ <- PasskeyDB.updateCounter(request.user, verifiedAuthData)
          _ <- PasskeyDB.updateLastUsedTime(request.user, verifiedAuthData)
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
        Some(Status(err.httpCode)(toJson(err)))
      case Failure(err) =>
        logger.error(err.getMessage, err)
        Some(Status(INTERNAL_SERVER_ERROR)(toJson(err)))
      case Success(_) => None
    }

  private def extractAuthenticationData[A](request: UserIdentityRequest[A]) =
    for {
      body <- request.body match {
        case AnyContentAsFormUrlEncoded(data) =>
          data
            .get("credentials")
            .flatMap(_.headOption)
            .toRight(
              JanusException.missingFieldInRequest(request.user, "credentials")
            )
            .toTry
        case AnyContentAsText(data) =>
          Option(data).toRight(JanusException.missingFieldInRequest(request.user, "body")).toTry
        case other =>
          Failure(
            JanusException.invalidRequest(
              request.user,
              s"Unexpected body type: ${other.getClass.getName}"
            )
          )
      }
      authData <- Passkey.parsedAuthentication(request.user, body)
    } yield authData
}
