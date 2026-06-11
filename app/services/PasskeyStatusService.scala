package services

import aws.PasskeyDB
import com.gu.googleauth.UserIdentity
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.{ExecutionContext, Future, blocking}

/** Determines whether a given user has at least one passkey registered. */
trait PasskeyStatusService {
  def userHasPasskey(user: UserIdentity): Future[Boolean]
}

/** Live implementation backed by a DynamoDB query. */
class LivePasskeyStatusService(using ExecutionContext, DynamoDbClient)
    extends PasskeyStatusService {
  def userHasPasskey(user: UserIdentity): Future[Boolean] =
    Future(blocking(PasskeyDB.hasPasskey(user))).flatMap(Future.fromTry)
}
