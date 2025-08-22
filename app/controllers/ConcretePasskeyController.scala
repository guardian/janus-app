package controllers

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.playpasskeyauth.controllers.BasePasskeyController
import com.gu.playpasskeyauth.services.PasskeyVerificationService
import com.gu.playpasskeyauth.web.RequestExtractor
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{
  ActionBuilder,
  AnyContent,
  AnyContentAsFormUrlEncoded,
  AnyContentAsJson,
  ControllerComponents
}

import scala.concurrent.ExecutionContext

class ConcretePasskeyController(
    controllerComponents: ControllerComponents,
    customAction: ActionBuilder[UserIdentityRequest, AnyContent],
    passkeyService: PasskeyVerificationService
)(using RequestExtractor[UserIdentityRequest], ExecutionContext)
    extends BasePasskeyController(
      controllerComponents,
      customAction,
      passkeyService
    )
