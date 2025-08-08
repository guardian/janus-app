package controllers

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.playpasskeyauth.controllers.BasePasskeyController
import com.gu.playpasskeyauth.services.PasskeyVerificationService
import com.gu.playpasskeyauth.web.RequestExtractor
import com.webauthn4j.data.AuthenticationData
import play.api.mvc.{ActionBuilder, AnyContent, ControllerComponents}

import scala.concurrent.ExecutionContext

given RequestExtractor[UserIdentityRequest] with {
  def findUserId[A](request: UserIdentityRequest[A]): Option[String] = Some(
    request.user.username
  )
  def findCreationData[A](request: UserIdentityRequest[A]): Option[String] = {
    request.body match {
      case content: AnyContent =>
        content.asFormUrlEncoded.flatMap(_.get("passkey").flatMap(_.headOption))
      case _ => None
    }
  }
  def findAuthenticationData[A](
      request: UserIdentityRequest[A]
  ): Option[AuthenticationData] = ???
}

class ConcretePasskeyController(
    controllerComponents: ControllerComponents,
    customAction: ActionBuilder[UserIdentityRequest, AnyContent],
    passkeyService: PasskeyVerificationService
)(using ExecutionContext)
    extends BasePasskeyController(
      controllerComponents,
      customAction,
      passkeyService
    )
