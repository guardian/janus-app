package controllers

import aws.Federation
import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import conf.Config
import logic.Revocation
import logic.UserAccess.*
import play.api.mvc.{
  AbstractController,
  Action,
  AnyContent,
  ControllerComponents
}
import play.api.{Configuration, Logging, Mode}
import software.amazon.awssdk.services.sts.StsClient

import java.time.{Instant, ZoneOffset}

class RevokePermissions(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    stsClient: StsClient,
    configuration: Configuration
)(using mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging {

  def revoke: Action[AnyContent] = authAction { implicit request =>
    val sortedAccounts = janusData.accounts.toList.sortBy(_.name.toLowerCase)
    Ok(views.html.revoke(sortedAccounts, request.user, janusData))
  }

  def revokeRequest(accountId: String): Action[AnyContent] = authAction {
    implicit request =>
      (for {
        account <- janusData.accounts.find(accountId == _.authConfigKey)
      } yield {
        logger.info(
          s"REVOKE request in started for $accountId by ${username(request.user)}"
        )
        Ok(views.html.revokeRequest(account, request.user, janusData))
          .withHeaders(CACHE_CONTROL -> "no-cache")
      }) getOrElse {
        logger.warn(
          s"Account not found: REVOKE request for $accountId by ${username(request.user)}"
        )
        NotFound(
          views.html.error("Account not found", Some(request.user), janusData)
        )
      }
  }

  def revokeConfirmation(accountId: Option[String]): Action[AnyContent] =
    authAction { implicit request =>
      if (accountId.isEmpty)
        Ok(views.html.revokeConfirmation(None, request.user, janusData))
      else {
        (for {
          account <- accountId
            .flatMap(aId => janusData.accounts.find(aId == _.authConfigKey))
        } yield {
          Ok(
            views.html
              .revokeConfirmation(Some(account), request.user, janusData)
          )
        }) getOrElse {
          logger.warn(
            s"Account not found: REVOKE confirmation screen for $accountId by ${username(request.user)}"
          )
          NotFound(
            views.html.error("Account not found", Some(request.user), janusData)
          )
        }
      }
    }

  def revokeAccount(accountId: String): Action[AnyContent] = authAction {
    implicit request =>
      val result = for {
        account <- janusData.accounts
          .find(accountId == _.authConfigKey)
          .toRight("Account not found")
        submission <- request.body.asFormUrlEncoded.toRight(
          "Could not parse submission"
        )
        confirmationKey <- submission
          .get("confirm")
          .flatMap(_.headOption)
          .toRight("Missing account confirmation")
        targetRoleArn = Config.roleArn(account.authConfigKey, configuration)
      } yield {
        if (Revocation.checkConfirmation(confirmationKey, account)) {
          Federation.disableFederation(
            account,
            Instant.now(),
            targetRoleArn,
            stsClient
          )
          logger.warn(
            s"Janus access revoked for $accountId by ${username(request.user)}"
          )
          Redirect(routes.RevokePermissions.revokeConfirmation(Some(accountId)))
        } else {
          logger.warn(
            s"Confirmation key $confirmationKey did not match for $accountId by ${username(request.user)}"
          )
          Redirect(routes.RevokePermissions.revokeRequest(accountId))
            .flashing(
              "confirmation-error" -> "Confirmation did not match the account."
            )
        }
      }
      result.fold(
        { errMsg =>
          logger.warn(
            s"$errMsg: denied REVOKE confirmation screen for $accountId by ${username(request.user)}"
          )
          BadRequest(views.html.error(errMsg, Some(request.user), janusData))
        },
        identity
      )
  }
}
