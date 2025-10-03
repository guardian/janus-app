package controllers

import aws.PasskeyDB
import com.gu.googleauth.AuthAction
import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.playpasskeyauth.web.RequestWithAuthenticationData
import filters.ConditionalAction
import play.api.Logging
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents, Request, Result}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.{ExecutionContext, Future}

/** Example controller demonstrating how to use the improved ConditionalAction with ActionRefiner.
  *
  * This shows three different approaches:
  * 1. ActionRefiner + ActionFilter composition (most Play-like)
  * 2. Single combined filter (simplest)
  * 3. Generic conditional logic
  */
class ExampleConditionalController(
    val controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent]
)(using dynamoDb: DynamoDbClient, ec: ExecutionContext) extends BaseController with Logging {

  /** Predicate function that checks if a user has registered passkeys */
  private def userHasPasskeys(request: UserIdentityRequest[_]): Future[Boolean] = {
    PasskeyDB.loadCredentials(request.user).fold(
      err => {
        // On error, assume no passkeys (could also fail the request)
        logger.error(s"Failed to check passkeys for user ${request.user.username}", err)
        Future.successful(false)
      },
      dbResponse => Future.successful(!dbResponse.items.isEmpty)
    )
  }

  /** Request enricher that transforms UserIdentityRequest to RequestWithAuthenticationData
    * This creates a proper RequestWithAuthenticationData from the UserIdentityRequest
    */
  private def enrichRequest(request: UserIdentityRequest[_]): RequestWithAuthenticationData[_] = {
    // Create RequestWithAuthenticationData using the proper constructor
    // Based on the compilation errors, it needs authenticationData (JsValue) and request parameters
    import play.api.libs.json.{JsNull, JsValue}

    // Cast the request to the expected type for the constructor
    val typedRequest = request.asInstanceOf[UserIdentityRequest[AnyContent]]

    // Create with placeholder authentication data - in practice this would come from the passkey verification
    new RequestWithAuthenticationData(
      authenticationData = JsNull, // Placeholder - would be actual auth data from passkey verification
      request = typedRequest
    )
  }

  /** Passkey verification logic extracted from the filter */
  private def passkeyVerificationLogic(request: RequestWithAuthenticationData[_]): Future[Option[Result]] = {
    // This is where you'd implement the actual passkey verification logic
    // that's currently in your passkeyAuthFilter

    // For example, you might:
    // 1. Validate the authentication data
    // 2. Check signatures
    // 3. Verify the challenge
    // 4. Return None to allow, or Some(Result) to reject

    // Placeholder - replace with actual passkey verification from your filter
    Future.successful(None) // Allow for now
  }

  /** Approach 1: Using ActionRefiner + ActionFilter composition (recommended) */
  def sensitiveActionWithRefiner: Action[AnyContent] = {
    val (refiner, authFilter) = ConditionalAction.forPasskeys(
      userHasPasskeys,
      passkeyVerificationLogic,
      enrichRequest
    )

    (authAction andThen refiner andThen authFilter) { request =>
      // The request here could be either UserIdentityRequest or RequestWithAuthenticationData
      // depending on whether the user has passkeys
      Ok(s"Sensitive action executed for user: ${getUsername(request)}")
    }
  }

  /** Approach 2: Using single combined filter (simpler) */
  def sensitiveAction: Action[AnyContent] = {
    val passkeyFilter = ConditionalAction.passkeyFilter(
      userHasPasskeys,
      passkeyVerificationLogic,
      enrichRequest
    )

    (authAction andThen passkeyFilter) { request =>
      // This action will:
      // 1. Require Google Auth (from authAction)
      // 2. If user has passkeys, require passkey verification
      // 3. If user has no passkeys, proceed directly
      Ok(s"Sensitive action executed for user: ${request.user.username}")
    }
  }

  /** Approach 3: Using generic conditional logic */
  def anotherExample: Action[AnyContent] = {
    // Custom predicate - check if user email contains "admin"
    val isAdminPredicate: UserIdentityRequest[Any] => Future[Boolean] = { request =>
      Future.successful(request.user.email.contains("admin"))
    }

    // Admin verification logic
    val adminLogic: UserIdentityRequest[Any] => Future[Option[Result]] = { request =>
      if (request.user.email.endsWith("@guardian.co.uk")) {
        Future.successful(None) // Allow
      } else {
        Future.successful(Some(Forbidden("Admin access required")))
      }
    }

    val conditionalFilter = ConditionalAction.conditional[UserIdentityRequest](
      isAdminPredicate,
      adminLogic
    )

    (authAction andThen conditionalFilter) { request =>
      Ok(s"Action executed with conditional logic for: ${request.user.username}")
    }
  }

  /** Helper to extract username from different request types */
  private def getUsername(request: Request[_]): String = {
    // Use raw type checking to avoid type erasure issues
    if (request.getClass.getName.contains("UserIdentityRequest")) {
      // Safe to cast since we checked the class name
      request.asInstanceOf[UserIdentityRequest[_]].user.username
    } else if (request.getClass.getName.contains("RequestWithAuthenticationData")) {
      // Safe to cast since we checked the class name
      request.asInstanceOf[RequestWithAuthenticationData[_]].user.username
    } else {
      "unknown"
    }
  }
}
