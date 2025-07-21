package filters

import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.googleauth.UserIdentity
import com.gu.janus.model.JanusData
import com.webauthn4j.data.AuthenticationData
import controllers.AssetsFinder
import logic.Passkey
import models.JanusException
import models.JanusException.throwableWrites
import models.PasskeyFlow.Authentication
import play.api.{Logging, Mode}
import play.api.libs.json.Json.toJson
import play.api.mvc.Results.{InternalServerError, MethodNotAllowed, Ok, Status}
import play.api.mvc.{
  ActionFilter,
  AnyContentAsFormUrlEncoded,
  AnyContentAsText,
  RequestHeader,
  Result
}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Performs passkey verification and only allows an action to continue if
  * verification is successful.
  *
  * See
  * [[https://webauthn4j.github.io/webauthn4j/en/#webauthn-assertion-verification-and-post-processing]].
  */
class PasskeyAuthFilter(
    host: String,
    janusData: JanusData,
    passkeysEnabled: Boolean,
    enablingCookieName: String
)(using
    dynamoDb: DynamoDbClient,
    val executionContext: ExecutionContext,
    mode: Mode,
    assetsFinder: AssetsFinder
) extends ActionFilter[UserIdentityRequest]
    with Logging {

  def filter[A](request: UserIdentityRequest[A]): Future[Option[Result]] = {
    implicit val r: RequestHeader = request
    val enablingCookieIsPresent =
      request.cookies.get(enablingCookieName).isDefined
    if (passkeysEnabled && enablingCookieIsPresent) {
      request.method match {
        case "GET" =>
          logger.info(
            s"Showing passkey auth page to ${request.user.username} ..."
          )
          Future(Some(Ok(views.html.passkeyAuth(request.user, janusData))))
        case "POST" =>
          logger.info(
            s"Verifying passkey for user ${request.user.username} ..."
          )
          Future(apiResponse(authenticatePasskey(request)))
        case _ =>
          Future(Some(MethodNotAllowed(request.method)))
      }
    } else {
      logger.info(
        s"Passing through request for '${request.method} ${request.path}' by ${request.user.username}"
      )
      Future.successful(None)
    }
  }

  private def authenticatePasskey[A](
      request: UserIdentityRequest[A]
  ): Try[Unit] = for {
    challengeResponse <- PasskeyChallengeDB.loadChallenge(
      request.user,
      Authentication
    )
    challenge <- PasskeyChallengeDB.extractChallenge(
      challengeResponse,
      request.user
    )
    authData <- extractAuthDataFromRequest(request)
    credentialResponse <- PasskeyDB.loadCredential(
      request.user,
      authData.getCredentialId
    )
    _ <- validateCredentialExists(request.user, credentialResponse)
    credential <- PasskeyDB.extractCredential(credentialResponse, request.user)
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
    _ = logger.info(s"Verified passkey for user ${request.user.username}")
  } yield ()

  private def apiResponse(auth: => Try[Unit]): Option[Result] =
    auth match {
      case Failure(err: JanusException) =>
        logger.error(err.engineerMessage, err.causedBy.orNull)
        Some(Status(err.httpCode)(toJson(err)))
      case Failure(err) =>
        logger.error(err.getMessage, err)
        Some(InternalServerError(toJson(err)))
      case Success(_) => None
    }

  private def extractAuthDataFromRequest[A](
      request: UserIdentityRequest[A]
  ): Try[AuthenticationData] = for {
    body <- extractRequestBody(request)
    authData <- Passkey.parsedAuthentication(request.user, body)
  } yield authData

  private def extractRequestBody[A](
      request: UserIdentityRequest[A]
  ): Try[String] = request.body match {
    case AnyContentAsFormUrlEncoded(data) =>
      data
        .get("credentials")
        .flatMap(_.headOption)
        .toRight(
          JanusException.missingFieldInRequest(request.user, "credentials")
        )
        .toTry
    case AnyContentAsText(data) =>
      Option(data)
        .toRight(JanusException.missingFieldInRequest(request.user, "body"))
        .toTry
    case other =>
      Failure(
        JanusException.invalidRequest(
          request.user,
          s"Unexpected body type: ${other.getClass.getName}"
        )
      )
  }

  private def validateCredentialExists(
      user: UserIdentity,
      credentialResponse: GetItemResponse
  ): Try[Unit] =
    if (credentialResponse.hasItem) Success(())
    else
      Failure(
        JanusException.authenticationFailure(
          user,
          new IllegalArgumentException("Invalid passkey credential")
        )
      )
}
