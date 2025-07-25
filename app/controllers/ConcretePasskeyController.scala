package controllers

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.playpasskeyauth.controllers.BasePasskeyController
import com.gu.playpasskeyauth.services.PasskeyVerificationService
import com.gu.playpasskeyauth.web.RequestHelper
import com.webauthn4j.data.AuthenticationData
import play.api.mvc.{ActionBuilder, AnyContent, ControllerComponents}

import scala.concurrent.ExecutionContext

given RequestHelper[UserIdentityRequest] with {
  def findUserId[A](request: UserIdentityRequest[A]): Option[String] = ???
  def findCreationData[A](request: UserIdentityRequest[A]): Option[String] = ???
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
