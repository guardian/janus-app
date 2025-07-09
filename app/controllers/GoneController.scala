package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import logic.Date
import play.api.Mode
import play.api.mvc.*

import java.time.{ZoneId, ZonedDateTime}

class GoneController(
    cc: ControllerComponents,
    authAction: AuthAction[AnyContent],
    janusData: JanusData
)(using mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(cc) {

  def gone: Action[Unit] = authAction(parse.empty) {
    implicit request =>
      val displayMode =
        Date.displayMode(ZonedDateTime.now(ZoneId.of("Europe/London")))
      Gone(views.html.gone(request.user, janusData, displayMode))
  }
}
