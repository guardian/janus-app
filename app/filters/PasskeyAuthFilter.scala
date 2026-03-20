package filters

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.playpasskeyauth.models.UserId
import com.gu.playpasskeyauth.services.{
  PasskeyException,
  PasskeyVerificationService
}
import models.JanusException
import models.JanusException.throwableWrites
import play.api.libs.json.{JsValue, Json}
import play.api.Logging
import play.api.libs.json.Json.toJson
import play.api.mvc.Results.{InternalServerError, Status, Unauthorized}
import play.api.mvc.{
  ActionFilter,
  AnyContentAsFormUrlEncoded,
  AnyContentAsText,
  Result
}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

/** Verifies passkeys and only allows an action to continue if verification is
  * successful. If passkeys are disabled globally or for the current request,
  * the action is allowed to continue.
  *
  * See
  * [[https://webauthn4j.github.io/webauthn4j/en/#webauthn-assertion-verification-and-post-processing]].
  */
class PasskeyAuthFilter(
    passkeyVerificationService: PasskeyVerificationService,
    passkeysEnabled: Boolean,
    enablingCookieName: String
)(using val executionContext: ExecutionContext)
    extends ActionFilter[UserIdentityRequest]
    with Logging {

  def filter[A](request: UserIdentityRequest[A]): Future[Option[Result]] = {
    val enablingCookieIsPresent =
      request.cookies.get(enablingCookieName).isDefined
    if (passkeysEnabled && enablingCookieIsPresent) {
      logger.info(s"Verifying passkey for user ${request.user.username} ...")
      authenticatePasskey(request)
        .map(_ => {
          logger.info(s"Verified passkey for user ${request.user.username}")
          None
        })
        .recover {
          case err: JanusException =>
            logger.error(err.engineerMessage, err.causedBy.orNull)
            Some(Status(err.httpCode)(toJson(err)))
          case err: PasskeyException =>
            logger.warn(
              s"Passkey verification failed for ${request.user.username}: ${err.getMessage}"
            )
            Some(
              Unauthorized(
                toJson(JanusException.authenticationFailure(request.user, err))
              )
            )
          case err =>
            logger.error(err.getMessage, err)
            Some(InternalServerError(toJson(err)))
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
  ): Future[Unit] =
    for {
      authData <- Future.fromTry(extractAuthDataFromRequest(request))
      _ <- passkeyVerificationService.verifyPasskey(
        UserId(request.user.username),
        authData
      )
    } yield ()

  private def extractAuthDataFromRequest[A](
      request: UserIdentityRequest[A]
  ): Try[JsValue] = for {
    body <- extractRequestBody(request)
    authData <- Try(Json.parse(body)).recoverWith(err =>
      Failure(
        JanusException.invalidFieldInRequest(request.user, "passkey", err)
      )
    )
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
}
