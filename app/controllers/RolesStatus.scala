package controllers

import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import conf.Config
import logic.Owners
import play.api.mvc.{
  AbstractController,
  Action,
  AnyContent,
  ControllerComponents
}
import play.api.{Configuration, Logging, Mode}
import software.amazon.awssdk.arns.Arn
import software.amazon.awssdk.services.sts.StsClient

import scala.jdk.OptionConverters.*
import scala.util.Try

class RolesStatus(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    stsClient: StsClient,
    configuration: Configuration
)(using mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging {

  // Temporary IamRoleInfo generation for testing with.
  private def rolesStatuses = (for {
    account <- Try(configuration.get[Configuration]("federation")).fold(
      _ => Set("failed"),
      federationConfig => {
        federationConfig.keys
          .flatMap(k => {
            println(k);
            println(federationConfig.get[String](k));
            Arn.fromString(federationConfig.get[String](k)).accountId().toScala
          })
          .toList
      }
    )
    _ = println(account)
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
      case Some(accountIdTry) if accountIdTry.isSuccess =>
        rolesStatuses.filter(roleStatus =>
          roleStatus.roleArn.accountId().toScala.contains(accountIdTry.get)
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

}

case class IamRoleInfo(
    roleName: String,
    roleArn: Arn,
    tags: Map[String, String]
)
