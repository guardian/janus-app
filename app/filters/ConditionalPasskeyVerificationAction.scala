package filters

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.playpasskeyauth.web.RequestWithAuthenticationData
import play.api.libs.json.Json
import play.api.mvc.*

import scala.concurrent.{ExecutionContext, Future}

/** ActionBuilder that conditionally applies passkey verification based on
  * configuration and cookie presence.
  *
  * If both passkeysEnabled is true and the enabling cookie is present, applies
  * applies passkey verification. Otherwise, applies authAction without
  * verification.
  */
class ConditionalPasskeyVerificationAction(
    passkeysEnabled: Boolean,
    enablingCookieName: String,
    authAction: ActionBuilder[UserIdentityRequest, AnyContent],
    verificationAction: ActionBuilder[
      RequestWithAuthenticationData,
      AnyContent
    ]
)(using val executionContext: ExecutionContext)
    extends ActionBuilder[RequestWithAuthenticationData, AnyContent] {

  override def parser: BodyParser[AnyContent] = authAction.parser

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
          // Both conditions met: apply verification action
          verificationAction.invokeBlock(request, block)
        } else {
          // Conditions not met: wrap in RequestWithAuthenticationData without verification
          val authRequest = new RequestWithAuthenticationData(
            Json.obj(),
            userRequest
          )
          block(authRequest)
        }
      }
    )
  }
}
