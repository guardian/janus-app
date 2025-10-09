package filters

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.playpasskeyauth.web.{
  AuthenticationDataExtractor,
  RequestWithAuthenticationData
}
import play.api.Logging
import play.api.libs.json.{JsObject, JsValue}
import play.api.mvc.{ActionTransformer, Request}

import scala.concurrent.{ExecutionContext, Future}

/** ActionTransformer that conditionally transforms UserIdentityRequest into
  * RequestWithAuthenticationData.
  *
  * If both passkeysEnabled is true and the enabling cookie is present,
  * transforms to RequestWithAuthenticationData. Otherwise, the request remains
  * as UserIdentityRequest (which extends Request, so this works with
  * ActionTransformer).
  *
  * This transformer is purely functional and follows the conditional
  * transformation logic requested.
  */
class ConditionalPasskeyTransformer(
    passkeysEnabled: Boolean,
    enablingCookieName: String,
    authenticationDataExtractor: AuthenticationDataExtractor
)(implicit val executionContext: ExecutionContext)
    extends ActionTransformer[UserIdentityRequest, Request]
    with Logging {

  override protected def transform[A](
      request: UserIdentityRequest[A]
  ): Future[Request[A]] = {
    Future.successful {
      if (shouldTransformToPasskeyRequest(request)) {
        logger.debug(
          s"Transforming to RequestWithAuthenticationData for user ${request.user.username}"
        )
        createAuthenticatedRequest(request)
      } else {
        logger.debug(
          s"Keeping as UserIdentityRequest for user ${request.user.username}"
        )
        request // UserIdentityRequest extends Request, so this is valid
      }
    }
  }

  /** Pure function to determine if transformation should occur based on config
    * and cookie presence
    */
  private def shouldTransformToPasskeyRequest[A](
      request: UserIdentityRequest[A]
  ): Boolean = {
    val enablingCookieIsPresent =
      request.cookies.get(enablingCookieName).isDefined
    passkeysEnabled && enablingCookieIsPresent
  }

  /** Pure function to create RequestWithAuthenticationData from
    * UserIdentityRequest
    */
  private def createAuthenticatedRequest[A](
      request: UserIdentityRequest[A]
  ): RequestWithAuthenticationData[A] = {
    val authenticationData: JsValue = authenticationDataExtractor
      .findAuthenticationData(request)
      .getOrElse(JsObject.empty)
    new RequestWithAuthenticationData(authenticationData, request)
  }
}
