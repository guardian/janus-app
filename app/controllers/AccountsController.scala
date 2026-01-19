package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.{AwsAccount, JanusData}
import conf.Config
import logic.Accounts
import models.{
  AwsAccountIamRoleInfoStatus,
  FailureSnapshot,
  IamRoleInfo,
  IamRoleInfoSnapshot
}
import play.api.mvc.*
import play.api.{Configuration, Logging, Mode}
import services.ProvisionedRoleStatusManager

import java.time.Duration
import scala.util.{Success, Try}

class AccountsController(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    configuration: Configuration,
    provisionedRoleStatusManager: ProvisionedRoleStatusManager
)(using mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging
    with Accounts {

  def lookupAccountId: AwsAccount => Try[String] = account =>
    Config.accountNumber(account.authConfigKey, configuration)

  def lookupAccountRoles: (AwsAccount, Try[String]) => Set[IamRoleInfo] =
    (account, accountIdMaybe) =>
      accountIdMaybe match {
        case Success(accountId) =>
          rolesStatuses.get(account) match {
            case Some(
                  AwsAccountIamRoleInfoStatus(
                    Some(IamRoleInfoSnapshot(roles, _)),
                    _
                  )
                ) =>
              roles.toSet
            case _ => Set.empty
          }
        case _ => Set.empty
      }

  def accounts: Action[AnyContent] = authAction { implicit request =>
    val accountData = accountOwnerInformation(
      janusData.accounts,
      janusData.access
    )(lookupAccountId, lookupAccountRoles)

    // log any account number errors we accumulated
    accountIdErrors(accountData)
      .foreach { case (account, err) =>
        logger
          .warn(
            s"Couldn't lookup account number for ${account.authConfigKey}",
            err
          )
      }
    Ok(views.html.accounts(accountData, request.user, janusData))
  }

  private def rolesStatuses: Map[AwsAccount, AwsAccountIamRoleInfoStatus] =
    provisionedRoleStatusManager.getCacheStatus

  def accountRoles: Action[AnyContent] = authAction { implicit request =>
    val successfulRoles = rolesStatuses
      .flatMap { (k, v) =>
        v match {
          case AwsAccountIamRoleInfoStatus(
                Some(
                  IamRoleInfoSnapshot(iamRoles, _)
                ),
                _
              ) =>
            iamRoles.map(role =>
              ((role.friendlyName, role.provisionedRoleTagValue), k.name)
            )
          case _ => Seq.empty[((Option[String], String), String)]
        }
      }
      .groupMap(_._1)(_._2)

    val failedRoles = rolesStatuses
      .map { (k, v) =>
        v match {
          case AwsAccountIamRoleInfoStatus(
                _,
                Some(
                  FailureSnapshot(failure, _)
                )
              ) =>
            (k.name, Some(failure))
          case _ => (k.name, None)
        }
      }
      .groupMap(_._1)(_._2)

    Ok(
      views.html
        .accountRoles(
          successfulRoles,
          failedRoles,
          request.user,
          janusData
        )
    )
  }

  private def accountOwnersLookup(account: String) = accountOwnerInformation(
    janusData.accounts,
    janusData.access
  )(lookupAccountId, lookupAccountRoles)
    .find(_.account.authConfigKey == account)

  def rolesStatusForAccount(account: String): Action[AnyContent] = {

    val accountName =
      janusData.accounts.find(_.authConfigKey == account).map(_.name)

    val successfulRolesForThisAccount: List[IamRoleInfo] = {
      rolesStatuses.find(_._1.authConfigKey == account) match {
        case Some(
              _,
              AwsAccountIamRoleInfoStatus(
                Some(IamRoleInfoSnapshot(roles, _)),
                _
              )
            ) =>
          roles
        case _ => Nil
      }
    }

    val error: Option[String] = {
      rolesStatuses.find(_._1.authConfigKey == account) match {
        case Some(
              _,
              AwsAccountIamRoleInfoStatus(
                _,
                Some(FailureSnapshot(failure, _))
              )
            ) =>
          Some(failure)
        case _ => None
      }
    }

    authAction { implicit request =>
      Ok(
        views.html.rolesStatus(
          accountName.getOrElse("Unknown Account"),
          successfulRolesForThisAccount,
          error,
          request.user,
          janusData
        )
      )
    }
  }

  def usersForAccount(account: String): Action[AnyContent] = {

    val usersForThisAccount: List[String] =
      accountOwnersLookup(account).map(_.permissions.map(_.userName)) match {
        case Some(users) => users
        case _           => List.empty
      }

    authAction { implicit request =>
      Ok(
        views.html.users(
          account,
          usersForThisAccount,
          request.user,
          janusData
        )
      )
    }
  }

  def accountInfo(account: String): Action[AnyContent] = {
    val accountName =
      janusData.accounts.find(_.authConfigKey == account).map(_.name)

    val accountUsers: List[String] = accountOwnerInformation(
      janusData.accounts,
      janusData.access
    )(lookupAccountId, lookupAccountRoles)
      .find(accountInfo => accountInfo.account.authConfigKey == account)
      .map(_.permissions.map(_.userName))
      .getOrElse(Nil)

    authAction { implicit request =>
      Ok(
        views.html.accountInfo(
          accountName.getOrElse("Unknown account"),
          account,
          accountUsers,
          request.user,
          janusData
        )
      )
    }
  }

  def provisionedRoleStatus: Action[AnyContent] = authAction {
    implicit request =>
      Ok(
        views.html.provisionedRoleStatus(
          provisionedRoleStatusManager.getCacheStatus,
          request.user,
          janusData
        )
      )
  }

}
