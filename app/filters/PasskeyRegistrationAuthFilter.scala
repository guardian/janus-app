package filters

import aws.PasskeyDB
import com.gu.googleauth.AuthAction.UserIdentityRequest
import controllers.PasskeyAuthFilter
import models.JanusException
import models.JanusException.throwableWrites
import play.api.Logging
import play.api.libs.json.Json.toJson
import play.api.mvc.Results.{InternalServerError, Status}
import play.api.mvc.{ActionFilter, Result}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** This filter contains conditional logic to decide which filter to apply to a
  * request to register a passkey. If the user has no existing passkeys, the
  * request is delegated to the Google reauthentication filter. If the user
  * already has passkeys, the request is delegated to the standard
  * PasskeyAuthFilter.
  */
class PasskeyRegistrationAuthFilter(
    googleReauthFilter: GoogleReauthFilter,
    passkeyAuthFilter: PasskeyAuthFilter
)(using
    dynamoDb: DynamoDbClient,
    val executionContext: ExecutionContext
) extends ActionFilter[UserIdentityRequest]
    with Logging {

  def filter[A](request: UserIdentityRequest[A]): Future[Option[Result]] = {
    PasskeyDB
      .loadCredentials(request.user)
      .map(dbResponse => !dbResponse.items.isEmpty) match {
      case Success(hasPasskey) =>
        if hasPasskey then passkeyAuthFilter.filter(request)
        else googleReauthFilter.filter(request)

      case Failure(err: JanusException) =>
        logger.error(err.engineerMessage, err.causedBy.orNull)
        Future.successful(Some(Status(err.httpCode)(toJson(err))))

      case Failure(err) =>
        logger.error(err.getMessage, err)
        Future.successful(Some(InternalServerError(toJson(err))))
    }
  }
}
