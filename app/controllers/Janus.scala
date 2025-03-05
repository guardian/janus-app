package controllers

import aws.{AuditTrailDB, Federation}
import cats.syntax.all._
import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.janus.model._
import conf.Config
import logic.PlayHelpers.splitQuerystringParam
import logic.{AuditTrail, Customisation, Date, Favourites}
import play.api.{Configuration, Logging, Mode}
import play.api.mvc._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.Credentials

import java.time._

class Janus(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    host: String,
    stsClient: StsClient,
    configuration: Configuration
)(implicit dynamodDB: DynamoDbClient, mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging {

  import logic.AccountOrdering._
  import logic.UserAccess._

  def index = authAction { implicit request =>
    val displayMode = Date.displayMode(ZonedDateTime.now(ZoneId.of("Europe/London")))
    (for {
      permissions <- userAccess(username(request.user), janusData.access)
      favourites = Favourites.fromCookie(request.cookies.get("favourites"))
      awsAccountAccess = orderedAccountAccess(permissions, favourites)
    } yield {
      Ok(
        views.html.index(awsAccountAccess, request.user, janusData, displayMode)
      )
    }) getOrElse Ok(views.html.noPermissions(request.user, janusData))
  }

  def admin = authAction { implicit request =>
    (for {
      permissions <- userAccess(username(request.user), janusData.admin)
      awsAccountAccess = orderedAccountAccess(permissions)
    } yield {
      Ok(views.html.admin(awsAccountAccess, request.user, janusData))
    }) getOrElse Ok(
      views.html
        .error("You do not have admin access", Some(request.user), janusData)
    )
  }

  def support = authAction { implicit request =>
    val now = Instant.now()
    val currentSupportUsers = activeSupportUsers(now, janusData.support)
    val supportUsersInNextPeriod = nextSupportUsers(now, janusData.support)
    val currentUserFutureSupportPeriods =
      futureRotaSlotsForUser(now, janusData.support, username(request.user))
    (for {
      permissions <- userSupportAccess(
        username(request.user),
        now,
        janusData.support
      )
      awsAccountAccess = orderedAccountAccess(permissions)
    } yield {
      Ok(
        views.html.support.support(
          awsAccountAccess,
          currentSupportUsers,
          supportUsersInNextPeriod,
          currentUserFutureSupportPeriods,
          request.user,
          janusData
        )
      )
    }) getOrElse Ok(
      views.html.support.notSupport(
        currentSupportUsers,
        supportUsersInNextPeriod,
        currentUserFutureSupportPeriods,
        request.user,
        janusData
      )
    )
  }

  def consoleLogin(permissionId: String) = authAction { implicit request =>
    (for {
      (credentials, _) <- assumeRole(
        request.user,
        permissionId,
        JConsole,
        Customisation.durationParams(request)
      )
      loginUrl = Federation.generateLoginUrl(credentials, host)
    } yield {
      SeeOther(loginUrl)
        .withHeaders(CACHE_CONTROL -> "no-cache")
    }) getOrElse {
      logger.warn(
        s"console login to $permissionId denied for ${username(request.user)}"
      )
      Forbidden(views.html.permissionDenied(request.user, janusData))
    }
  }

  def consoleUrl(permissionId: String) = authAction { implicit request =>
    (for {
      (credentials, permission) <- assumeRole(
        request.user,
        permissionId,
        JConsole,
        Customisation.durationParams(request)
      )
      loginUrl = Federation.generateLoginUrl(credentials, host)
    } yield {
      Ok(
        views.html.consoleUrl(
          loginUrl,
          permission.account.name,
          credentials,
          request.user,
          janusData
        )
      )
        .withHeaders(CACHE_CONTROL -> "no-cache")
    }) getOrElse {
      logger.warn(
        s"console login to $permissionId denied for ${username(request.user)}"
      )
      Forbidden(views.html.permissionDenied(request.user, janusData))
    }
  }

  def credentials(permissionId: String) = authAction { implicit request =>
    (for {
      (credentials, permission) <- assumeRole(
        request.user,
        permissionId,
        JCredentials,
        Customisation.durationParams(request)
      )
    } yield {
      Ok(
        views.html.credentials(
          credentials.expiration,
          List((permission.account, credentials)),
          request.user,
          janusData
        )
      )
        .withHeaders(CACHE_CONTROL -> "no-cache")
    }) getOrElse {
      logger.warn(
        s"denied credentials to $permissionId for ${username(request.user)}"
      )
      Forbidden(views.html.permissionDenied(request.user, janusData))
    }
  }

  def multiCredentials(rawPermissionIds: String) = authAction {
    implicit request =>
      val permissionIds = splitQuerystringParam(rawPermissionIds)
      (for {
        accountCredentials <- multiAccountAssumption(
          request.user,
          permissionIds,
          Customisation.durationParams(request)
        )
        expiry <- accountCredentials.headOption.map { case (_, creds) =>
          creds.expiration
        }
      } yield {
        Ok(
          views.html
            .credentials(expiry, accountCredentials, request.user, janusData)
        )
          .withHeaders(CACHE_CONTROL -> "no-cache")
      }) getOrElse {
        logger.warn(
          s"denied credentials to $rawPermissionIds for ${username(request.user)}"
        )
        Forbidden(views.html.permissionDenied(request.user, janusData))
      }
  }

  def favourite() = authAction { implicit request =>
    (for {
      submission <- request.body.asFormUrlEncoded
      accountSubmission <- submission.get("account")
      account <- accountSubmission.headOption
      favourites = Favourites.fromCookie(request.cookies.get("favourites"))
      newFavourites = Favourites.toggleFavourite(account, favourites)
    } yield {
      Redirect(routes.Janus.index)
        .withCookies(Favourites.toCookie(newFavourites))
    }) getOrElse Ok(
      views.html
        .error("Invalid favourite submission", Some(request.user), janusData)
    )
  }

  private def assumeRole(
      user: UserIdentity,
      permissionId: String,
      accessType: JanusAccessType,
      durationParams: (Option[Duration], Option[ZoneId])
  ): Option[(Credentials, Permission)] = {
    val (requestedDuration, tzOffset) = durationParams
    for {
      permission <- checkUserPermission(
        username(user),
        permissionId,
        Instant.now(),
        janusData.access,
        janusData.admin,
        janusData.support
      )
      duration = Federation.duration(
        permission,
        requestedDuration,
        tzOffset.map(Clock.system)
      )
      roleArn = Config.roleArn(permission.account.authConfigKey, configuration)
      credentials = Federation.assumeRole(
        username(user),
        roleArn,
        permission,
        stsClient,
        duration
      )
      auditLog = AuditTrail.createLog(
        user,
        permission,
        accessType,
        duration,
        janusData.access
      )
      _ = AuditTrailDB.insert(auditLog)
    } yield {
      logger.info(
        s"$accessType access to $permissionId granted for ${username(user)}"
      )
      (credentials, permission)
    }
  }

  private def multiAccountAssumption(
      user: UserIdentity,
      permissionIds: List[String],
      durationParams: (Option[Duration], Option[ZoneId])
  ): Option[List[(AwsAccount, Credentials)]] = {
    permissionIds
      .map(assumeRole(user, _, JCredentials, durationParams))
      .map(_.map { case (credentials, permission) =>
        permission.account -> credentials
      })
      .sequence
  }
}
