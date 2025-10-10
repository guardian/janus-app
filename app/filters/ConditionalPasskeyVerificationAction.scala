package filters

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.playpasskeyauth.web.{
  AuthenticationDataExtractor,
  RequestWithAuthenticationData
}
import play.api.libs.json.Json
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, Request, Result}

import scala.concurrent.{ExecutionContext, Future}

/** ActionBuilder that conditionally applies passkey verification based on
  * configuration and cookie presence.
  *
  * If both passkeysEnabled is true and the enabling cookie is present, applies
  * passkey verification. Otherwise, passes the request directly to the
  * controller without verification.
  */
class ConditionalPasskeyVerificationAction(
                                            passkeysEnabled: Boolean,
                                            enablingCookieName: String,
                                            authAction: ActionBuilder[UserIdentityRequest, AnyContent],
                                            verificationAction: ActionBuilder[RequestWithAuthenticationData, AnyContent],
                                            authenticationDataExtractor: AuthenticationDataExtractor
)(using val executionContext: ExecutionContext)
    extends ActionBuilder[RequestWithAuthenticationData, AnyContent] {

  override def parser: BodyParser[AnyContent] = verificationAction.parser

  override def invokeBlock[A](
      request: Request[A],
      block: RequestWithAuthenticationData[A] => Future[Result]
  ): Future[Result] = {
    authAction.invokeBlock(
      request,
      { (userRequest: UserIdentityRequest[A]) =>
        val shouldVerify = passkeysEnabled && userRequest.cookies
          .get(enablingCookieName)
          .isDefined

        if (shouldVerify) {
          // Both conditions met: refine to RequestWithAuthenticationData and verify
          verificationAction.invokeBlock(request, block)
        } else {
          // Conditions not met: pass directly to controller
          val authRequest = new RequestWithAuthenticationData(
              authenticationDataExtractor.findAuthenticationData(userRequest).getOrElse(Json.obj()),
              userRequest
              )
          block(authRequest)
        }
      }
    )
  }
}
