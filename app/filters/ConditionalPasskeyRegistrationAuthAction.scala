package filters

import aws.PasskeyDB
import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.playpasskeyauth.web.RequestWithAuthenticationData
import play.api.Logging
import play.api.mvc.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** ActionBuilder that conditionally applies passkey verification based on
  * configuration, cookie presence, and database credentials.
  *
  * If passkeysEnabled is true AND the enabling cookie is present AND the user
  * has passkey credentials in the database, applies passkey verification.
  * Otherwise, passes the request directly to the controller without verification.
  */
class ConditionalPasskeyRegistrationAuthAction(
    passkeysEnabled: Boolean,
    enablingCookieName: String,
    authAction: ActionBuilder[UserIdentityRequest, AnyContent],
    verificationAction: ActionBuilder[RequestWithAuthenticationData, AnyContent]
)(using dynamoDb: DynamoDbClient, val executionContext: ExecutionContext)
    extends ActionBuilder[UserIdentityRequest, AnyContent]
    with Logging {

  override def parser: BodyParser[AnyContent] = authAction.parser

  private def hasPasskeyCredentials[A](
      request: UserIdentityRequest[A]
  ): Either[Result, Boolean] = {
    PasskeyDB.loadCredentials(request.user) match {
      case Success(dbResponse) => Right(!dbResponse.items.isEmpty)
      case Failure(e) =>
        logger.error(s"Failed to load passkey credentials: ${e.getMessage}", e)
        Left(Results.InternalServerError("Failed to verify credentials"))
    }
  }

  def invokeBlock[A](
      request: Request[A],
      block: UserIdentityRequest[A] => Future[Result]
  ): Future[Result] = {
    authAction.invokeBlock(
      request,
      { (userRequest: UserIdentityRequest[A]) =>
        val hasCookie = userRequest.cookies.get(enablingCookieName).isDefined
        val shouldCheckCredentials = passkeysEnabled && hasCookie

        if (shouldCheckCredentials) {
          hasPasskeyCredentials(userRequest) match {
            case Left(errorResult) => Future.successful(errorResult)
            case Right(hasCredentials) =>
              if (hasCredentials) {
                // All conditions met: apply verification
                verificationAction.invokeBlock(
                  request,
                  { (_: RequestWithAuthenticationData[A]) => block(userRequest) }
                )
              } else {
                // No credentials in database: bypass verification
                block(userRequest)
              }
          }
        } else {
          // Config disabled or cookie not present: bypass verification
          block(userRequest)
        }
      }
    )
  }
}
