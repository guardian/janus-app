package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import logic.Owners
import play.api.mvc._


class Utility(janusData: JanusData, controllerComponents: ControllerComponents, authAction: AuthAction[AnyContent])(implicit assetsFinder: AssetsFinder)
  extends AbstractController(controllerComponents) {

  def healthcheck = Action {
    Ok("ok")
  }

  def help = authAction { implicit request =>
    Ok(views.html.help(request.user, janusData))
  }

  def accounts = authAction { implicit request =>
    val sortedAccounts = janusData.accounts.toList.sortBy(_.name.toLowerCase)
    val owners = sortedAccounts.map(account => account -> Owners.accountOwners(account, janusData.access))
    Ok(views.html.accounts(owners, request.user, janusData))
  }
}
