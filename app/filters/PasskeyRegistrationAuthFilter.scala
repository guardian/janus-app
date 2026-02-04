package filters

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.googleauth.UserIdentity
import com.gu.playpasskeyauth.models.{PasskeyUser, UserId}
import com.gu.playpasskeyauth.services.PasskeyRepository
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.Results.InternalServerError
import play.api.mvc.{ActionFilter, Result}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.{ExecutionContext, Future}

/** This filter determines if a user should be allowed to register passkeys:
  *   - If the user has no existing passkey credentials in the database, they're
  *     allowed to register (filter returns None)
  *   - If the user already has passkey credentials, the request is delegated to
  *     the standard PasskeyAuthFilter
  *
  * @param authFilter
  *   The passkey authentication filter to delegate to if the user already has
  *   credentials
  * @param dynamoDb
  *   The AWS DynamoDB client for database access
  * @param ec
  *   Execution context for asynchronous operations
  */
class PasskeyRegistrationAuthFilter(
    authFilter: PasskeyAuthFilter,
    passkeyDb: PasskeyRepository
)(using
    dynamoDb: DynamoDbClient,
    ec: ExecutionContext,
    passkeyUser: PasskeyUser[UserIdentity]
) extends ActionFilter[UserIdentityRequest]
    with Logging {

  def executionContext: ExecutionContext = ec

  /** Filters requests by checking if the user has registered passkeys.
    *
    * @param request
    *   The incoming user identity request
    * @tparam A
    *   The request body type
    * @return
    *   None if the user has no passkeys and can proceed with registration,
    *   otherwise the result of the delegated passkey auth filter or an error
    *   response
    */
  def filter[A](request: UserIdentityRequest[A]): Future[Option[Result]] =
    passkeyDb
      .loadPasskeyIds(UserId.from(request.user))
      .flatMap { ids =>
        if ids.nonEmpty then authFilter.filter(request)
        else Future.successful(None)
      }
}
