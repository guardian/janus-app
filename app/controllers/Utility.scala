package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import conf.Config
import logic.Owners
import models.IamRoleInfo
import play.api.mvc.*
import play.api.{Configuration, Logging, Mode}
import software.amazon.awssdk.arns.Arn
import scala.jdk.OptionConverters.*

import java.time.Duration
import scala.util.{Failure, Success, Try}

class Utility(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    configuration: Configuration,
    passkeysEnablingCookieName: String
)(using mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging {

  def healthcheck: Action[AnyContent] = Action {
    Ok("ok")
  }

  def accounts: Action[AnyContent] = authAction { implicit request =>
    val accountData = Owners.accountOwnerInformation(
      janusData.accounts.toList,
      janusData.access
    )(account => Config.accountNumber(account.authConfigKey, configuration))

    // log any account number errors we accumulated
    Owners
      .accountIdErrors(accountData)
      .foreach { case (account, err) =>
        logger
          .warn(s"Couldn't lookup account number for ${account.name}", err)
      }
    Ok(views.html.accounts(accountData, request.user, janusData))
  }

  /* Temporary IamRoleInfo generation for testing with. */
  private def rolesStatuses = (for {
    account <- Try(configuration.get[Configuration]("federation")).fold(
      _ => Set("failed"),
      federationConfig => {
        federationConfig.keys
          .flatMap(k => {
            Arn.fromString(federationConfig.get[String](k)).accountId().toScala
          })
          .toList
      }
    )
    i <- (1 to (Math.random() * 10 + 1).toInt)
  } yield {
    val tags = (1 to (Math.random() * 3 + 1).toInt)
      .map(j => s"test$i$j" -> s"testTag$i$j")
      .toMap
    IamRoleInfo(
      s"testName$i",
      Arn.fromString(
        s"arn:partition:service:region:$account:resource-type/resource-id-$i"
      ),
      tags
    )
  }).toSet

  def rolesStatus: Action[AnyContent] = authAction { implicit request =>
    Ok(
      views.html
        .rolesStatus("All", rolesStatuses, request.user, janusData)
    )
  }

  private val accountOwnersLookup = Owners
    .accountOwnerInformation(
      janusData.accounts.toList,
      janusData.access
    )(account => Config.accountNumber(account.authConfigKey, configuration))

  def rolesStatusForAccount(account: String): Action[AnyContent] = {
    val matchingAccountMaybe: Option[Try[String]] = accountOwnersLookup
      .find(_._1.name == account)
      .map(_._3)

    val rolesForThisAccount = matchingAccountMaybe match {
      case Some(Success(accountId)) =>
        rolesStatuses.filter(roleStatus =>
          roleStatus.roleArn.accountId().toScala.contains(accountId)
        )
      case _ => Set.empty
    }

    authAction { implicit request =>
      Ok(
        views.html.rolesStatus(
          account,
          rolesForThisAccount,
          request.user,
          janusData
        )
      )
    }
  }

  /** Temporary action to opt in to the passkeys integration */
  def optInToPasskeys: Action[AnyContent] = authAction { _ =>
    Redirect(routes.Janus.userAccount).withCookies(
      Cookie(
        name = passkeysEnablingCookieName,
        value = "true",
        maxAge = Some(Duration.ofDays(30).toSeconds.intValue)
      )
    )
  }

  /** Temporary action to opt out of the passkeys integration */
  def optOutOfPasskeys: Action[AnyContent] = authAction { _ =>
    Redirect(routes.Janus.userAccount)
      .discardingCookies(DiscardingCookie(passkeysEnablingCookieName))
  }
}
