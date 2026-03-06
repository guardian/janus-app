package filters

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.playpasskeyauth.models.UserId
import com.gu.playpasskeyauth.services.PasskeyRepository
import com.gu.playpasskeyauth.web.RequestWithAuthenticationData
import models.PasskeyRequest
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.*

import scala.concurrent.{ExecutionContext, Future}

/** ActionBuilder that conditionally applies passkey verification based on
  * configuration, cookie presence, and database credentials.
  *
  * If passkeysEnabled is true AND the enabling cookie is present AND the user
  * has passkey credentials in the database, applies passkey verification.
  * Otherwise, applies authAction without verification.
  */
class ConditionalPasskeyPreRegistrationVerificationAction(
    passkeyDb: PasskeyRepository,
    passkeysEnabled: Boolean,
    enablingCookieName: String,
    authAction: ActionBuilder[UserIdentityRequest, AnyContent],
    verificationAction: ActionBuilder[PasskeyRequest, AnyContent]
)(using val executionContext: ExecutionContext)
    extends ActionBuilder[PasskeyRequest, AnyContent]
    with Logging {

  override def parser: BodyParser[AnyContent] = authAction.parser

  private def hasPasskeyCredentials[A](
      request: UserIdentityRequest[A]
  ): Future[Boolean] =
    passkeyDb
      .list(UserId(request.user.username))
      .map(_.nonEmpty)

  override def invokeBlock[A](
      request: Request[A],
      block: PasskeyRequest[A] => Future[Result]
  ): Future[Result] = {
    authAction.invokeBlock(
      request,
      { (userRequest: UserIdentityRequest[A]) =>
        val hasCookie = userRequest.cookies.get(enablingCookieName).isDefined
        val shouldCheckCredentials = passkeysEnabled && hasCookie

        if (shouldCheckCredentials) {
          hasPasskeyCredentials(userRequest)
            .flatMap { hasCredentials =>
              if (hasCredentials) {
                // All conditions met: apply verification
                verificationAction.invokeBlock(request, block)
              } else {
                // No credentials in database: bypass verification
                val authRequest = RequestWithAuthenticationData(
                  Json.obj(),
                  userRequest.user,
                  userRequest
                )
                block(authRequest)
              }
            }
            .recoverWith { case e =>
              logger.error(
                s"Failed to load passkey credentials for ${userRequest.user.username}: ${e.getMessage}",
                e
              )
              Future.successful(
                Results.InternalServerError(
                  Json.obj("error" -> "Failed to verify passkey credentials")
                )
              )
            }
        } else {
          // Config disabled or cookie not present: bypass verification
          val authRequest = RequestWithAuthenticationData(
            Json.obj(),
            userRequest.user,
            userRequest
          )
          block(authRequest)
        }
      }
    )
  }
}
