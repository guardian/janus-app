package passkey

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.playpasskeyauth.web.RequestExtractor
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContentAsFormUrlEncoded, AnyContentAsJson}

given RequestExtractor[UserIdentityRequest] with {

  def findUserId[A](request: UserIdentityRequest[A]): Option[String] = Some(
    request.user.username
  )

  def findCreationData[A](request: UserIdentityRequest[A]): Option[JsValue] =
    request.body match {
      case AnyContentAsFormUrlEncoded(data) =>
        data.get("passkey").flatMap(_.headOption.map(Json.parse))
      case _ => None
    }

  def findAuthenticationData[A](
      request: UserIdentityRequest[A]
  ): Option[JsValue] =
    request.body match {
      case AnyContentAsFormUrlEncoded(data) =>
        data.get("credentials").flatMap(_.headOption.map(Json.parse))
      // Used in authenticate before registration context?
      case AnyContentAsJson(authData) => Some(authData)
      case _                          => None
    }
}
