package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import conf.Config
import logic.Owners
import play.api.{Configuration, Logging, Mode}
import play.api.mvc._

import scala.util.{Failure, Try}

class Utility(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    configuration: Configuration
)(implicit mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging {

  def healthcheck = Action {
    Ok("ok")
  }

  def accounts = authAction { implicit request =>
    val sortedAccounts = janusData.accounts.toList.sortBy(_.name.toLowerCase)
    val accountData =
      sortedAccounts.map { awsAccount =>
        (
          awsAccount,
          Owners.accountPermissions(awsAccount, janusData.access),
          Config.accountNumber(awsAccount.authConfigKey, configuration)
        )
      }
    // log any account number errors we accumulated
    accountData
      .collect { case (account, _, Failure(err)) =>
        (account, err)
      }
      .foreach { case (account, err) =>
        logger.warn(s"Couldn't lookup account number for ${account.name}", err)
      }
    Ok(views.html.accounts(accountData, request.user, janusData))
  }
}
