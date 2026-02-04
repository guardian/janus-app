package filters

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.googleauth.UserIdentity
import com.gu.playpasskeyauth.models.{PasskeyUser, UserId}
import com.gu.playpasskeyauth.services.PasskeyRepository
import com.gu.playpasskeyauth.web.RequestWithAuthenticationData
import models.PasskeyRequest
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

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
)(using
    dynamoDb: DynamoDbClient,
    val executionContext: ExecutionContext,
    passkeyUser: PasskeyUser[UserIdentity]
) extends ActionBuilder[PasskeyRequest, AnyContent]
    with Logging {

  override def parser: BodyParser[AnyContent] = authAction.parser

  private def hasPasskeyCredentials[A](
      request: UserIdentityRequest[A]
  )(using
      PasskeyUser[UserIdentity],
      ExecutionContext
  ): Either[Result, Boolean] = {
    Try(
      Await
        .result(passkeyDb.listPasskeys(UserId.from(request.user)), Duration.Inf)
    ) match {
      case Success(passkeys) => Right(passkeys.nonEmpty)
      case Failure(e)        =>
        logger.error(s"Failed to load passkey credentials: ${e.getMessage}", e)
        Left(Results.InternalServerError("Failed to verify credentials"))
    }
  }

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
          hasPasskeyCredentials(userRequest) match {
            case Left(errorResult)     => Future.successful(errorResult)
            case Right(hasCredentials) =>
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
