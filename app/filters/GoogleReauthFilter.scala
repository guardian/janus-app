package filters

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.googleauth.GoogleAuthFilters.LOGIN_ORIGIN_KEY
import com.gu.googleauth.{AuthAction, GoogleAuthConfig, LoginSupport}
import controllers.routes
import play.api.Logging
import play.api.libs.ws.WSClient
import play.api.mvc.*

import scala.concurrent.{ExecutionContext, Future}

/** A filter that requires users to reauthenticate with Google before accessing
  * a sensitive action.
  */
class GoogleReauthFilter(
    val authConfig: GoogleAuthConfig
)(using ec: ExecutionContext, val wsClient: WSClient)
    extends ActionFilter[UserIdentityRequest]
    with LoginSupport
    with Logging {

  override val failureRedirectTarget: Call = routes.AuthController.loginError
  override val defaultRedirectTarget: Call = routes.Janus.index

  def executionContext: ExecutionContext = ec

  def filter[A](
      request: UserIdentityRequest[A]
  ): Future[Option[Result]] = {
    val originalUrl = request.uri

    logger.info(
      s"Applying GoogleReauthFilter to request for '${request.uri}' from ${request.user.username}"
    )

    // Check if user needs fresh authentication
    val now = System.currentTimeMillis()
    val needsReauth =
      !request.session
        .get("reauth_time")
        .exists(reauthed => now - reauthed.toLong <= 10000) // 10 seconds

    if (needsReauth) {
      logger.info(
        s"Reauthenticating ${request.user.username}..."
      )

      startGoogleLogin()(request, ec).map(result =>
        Some(
          result.withSession(
            request.session +
              (LOGIN_ORIGIN_KEY -> originalUrl) - "reauth_time"
          )
        )
      )
    } else {
      // Authentication is fresh enough, allow the action to proceed
      Future.successful(None)
    }
  }
}
