package filters

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.playpasskeyauth.filters.PasskeyVerificationFilter
import play.api.Logging
import play.api.mvc.{ActionFilter, Result}

import scala.concurrent.{ExecutionContext, Future}

/** Allows an action to continue if passkeys are disabled globally or disabled
  * for the current request. Otherwise, applies the given verification filter to
  * the request.
  */
class ConditionalPasskeyVerificationFilter(
    passkeysEnabled: Boolean,
    enablingCookieName: String,
    verificationFilter: PasskeyVerificationFilter[UserIdentityRequest]
)(using val executionContext: ExecutionContext)
    extends ActionFilter[UserIdentityRequest]
    with Logging {

  def filter[A](request: UserIdentityRequest[A]): Future[Option[Result]] = {
    val enablingCookieIsPresent =
      request.cookies.get(enablingCookieName).isDefined
    if (passkeysEnabled && enablingCookieIsPresent) {
      logger.info(s"Verifying passkey for user ${request.user.username} ...")
      verificationFilter.filter(request)
    } else {
      logger.info(
        s"Passing through request for '${request.method} ${request.path}' by ${request.user.username}"
      )
      Future.successful(None)
    }
  }
}
