package filters

import aws.PasskeyDB
import com.gu.googleauth.AuthAction.UserIdentityRequest
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
class PasskeyRegistrationAuthFilter(authFilter: PasskeyAuthFilter)(using
    dynamoDb: DynamoDbClient,
    ec: ExecutionContext
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
    PasskeyDB
      .loadCredentials(request.user)
      .fold(
        err => {
          logger.error(
            s"Failed to load existing credentials for user ${request.user.username}",
            err
          )
          Future.successful(
            Some(
              InternalServerError(
                Json.obj(
                  "error" -> "DB load error",
                  "message" -> "Failed to load existing credentials for the user."
                )
              )
            )
          )
        },
        dbResponse =>
          if !dbResponse.items.isEmpty then authFilter.filter(request)
          else Future.successful(None)
      )
}
