package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import play.api.mvc.*
import play.api.{Configuration, Logging, Mode}

class Utility(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    configuration: Configuration
)(using mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging {

  def healthcheck: Action[AnyContent] = Action {
    Ok("ok")
  }
}
