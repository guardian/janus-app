package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import conf.Config
import logic.Accounts
import logic.Accounts.accountPermissions
import models.AwsAccountDeveloperPolicyStatus
import play.api.mvc.*
import play.api.{Configuration, Logging, Mode}
import services.DeveloperPolicyStatusManager

class AccountsController(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    configuration: Configuration,
    developerPolicyStatusManager: DeveloperPolicyStatusManager
)(using mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging {

  def accounts: Action[AnyContent] = authAction { implicit request =>
    val accountData = Accounts.accountOwnerInformation(
      developerPolicyStatusManager.getCacheStatus,
      janusData.accounts,
      janusData.access
    )(account => Config.findAccountNumber(account.authConfigKey, configuration))

    Ok(
      views.html.accounts(
        accountData,
        developerPolicyStatusManager.fetchEnabled,
        request.user,
        janusData
      )
    )
  }

  def policiesStatusForAccount(authConfigKey: String): Action[AnyContent] =
    authAction { implicit request =>
      (for {
        awsAccount <- janusData.accounts.find(_.authConfigKey == authConfigKey)
        developerPolicyCache = developerPolicyStatusManager.getCacheStatus
        accountPoliciesStatus = developerPolicyCache
          .getOrElse(awsAccount, AwsAccountDeveloperPolicyStatus.empty)
      } yield Ok(
        views.html.developerPoliciesStatus(
          awsAccount,
          accountPoliciesStatus,
          developerPolicyStatusManager.fetchEnabled,
          request.user,
          janusData
        )
      )).getOrElse {
        NotFound(
          views.html.error("Account not found", Some(request.user), janusData)
        )
      }
    }

  def usersForAccount(authConfigKey: String): Action[AnyContent] = authAction {
    implicit request =>
      janusData.accounts
        .find(_.authConfigKey == authConfigKey) match {
        case Some(awsAccount) =>
          val users =
            accountPermissions(awsAccount, janusData.access).map(_.userName)
          Ok(
            views.html.users(
              awsAccount.name,
              users,
              request.user,
              janusData
            )
          )
        case None =>
          NotFound(
            views.html.error("Account not found", Some(request.user), janusData)
          )
      }

  }

  def accountDeveloperPolicies: Action[AnyContent] = authAction {
    implicit request =>
      val accountsStatus =
        developerPolicyStatusManager.getCacheStatus.toList
          .sortBy(_._1.name)
      Ok(
        views.html
          .accountPolicies(
            accountsStatus,
            developerPolicyStatusManager.fetchEnabled,
            developerPolicyStatusManager.fetchRate,
            request.user,
            janusData
          )
      )
  }
}
