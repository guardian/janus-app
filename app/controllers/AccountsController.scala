package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.{AwsAccount, JanusData}
import conf.Config
import logic.Accounts
import logic.Accounts.accountPermissions
import models.{AwsAccountIamRoleInfoStatus, IamRoleInfo, IamRoleInfoSnapshot}
import play.api.mvc.*
import play.api.{Configuration, Logging, Mode}
import services.ProvisionedRoleStatusManager
import software.amazon.awssdk.arns.Arn

class AccountsController(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    configuration: Configuration,
    provisionedRoleStatusManager: ProvisionedRoleStatusManager
)(using mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging {

  def accounts: Action[AnyContent] = authAction { implicit request =>
    val accountData = Accounts.accountOwnerInformation(
      provisionedRoleStatusManager.getCacheStatus,
      janusData.accounts,
      janusData.access
    )(account => Config.findAccountNumber(account.authConfigKey, configuration))

    Ok(
      views.html.accounts(
        accountData,
        provisionedRoleStatusManager.fetchEnabled,
        request.user,
        janusData
      )
    )
  }

  def rolesStatusForAccount(authConfigKey: String): Action[AnyContent] =
    authAction { implicit request =>
      (for {
        awsAccount <- janusData.accounts.find(_.authConfigKey == authConfigKey)
        provisionedRolesCache = provisionedRoleStatusManager.getCacheStatus
        accountRolesStatus = provisionedRolesCache
          .getOrElse(awsAccount, AwsAccountIamRoleInfoStatus.empty)
      } yield Ok(
        views.html.rolesStatus(
          awsAccount,
          accountRolesStatus,
          provisionedRoleStatusManager.fetchEnabled,
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

  def accountRoles: Action[AnyContent] = authAction { implicit request =>
    val realData = provisionedRoleStatusManager.getCacheStatus
    // this fake data augments what gets fetched so we can design with populated accounts
    val fakeData = realData
      .map { case (account, status) =>
        val augmentedSnapshot =
          (status.roleSnapshot, status.failureStatus) match {
            case (None, Some(failure)) =>
              Some(
                IamRoleInfoSnapshot(
                  roles = List.fill(scala.util.Random.between(0, 11))(
                    IamRoleInfo(
                      roleArn = Arn.fromString(
                        s"arn:aws:iam::012345678901:role/gu/janus/discoverable/fake-role"
                      ),
                      provisionedRoleTagValue = "fake-provisioned-role",
                      friendlyName = Some("Fake Role"),
                      description = Some("This is a fake role for testing."),
                      account = account
                    )
                  ),
                  timestamp = java.time.Instant.now()
                )
              )
            case _ =>
              status.roleSnapshot

          }
        account -> status.copy(roleSnapshot = augmentedSnapshot)
      }
      .toList
      .sortBy(_._1.name)
    Ok(
      views.html
        .accountRoles(
          fakeData,
          provisionedRoleStatusManager.fetchEnabled,
          provisionedRoleStatusManager.fetchRate,
          request.user,
          janusData
        )
    )
  }
}
