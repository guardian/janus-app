package controllers

import aws.CloudWatchMetrics.*
import aws.{AuditTrailDB, CloudWatch, Federation, PasskeyDB}
import cats.syntax.all.*
import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.janus.model.*
import com.webauthn4j.data.attestation.authenticator.AAGUID
import conf.Config
import conf.Config.{passkeysManagerLink, passkeysManagerLinkText}
import logic.*
import logic.PlayHelpers.splitQuerystringParam
import models.AccessSource.Internal
import models.{
  AccessSource,
  AccountAccess,
  DeveloperPolicy,
  PasskeyAuthenticator
}
import play.api.mvc.*
import play.api.{Configuration, Logging, Mode}
import services.{DeveloperPolicyFinder, DeveloperPolicyStatusManager}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.Credentials

import java.time.*
import java.time.format.DateTimeFormatter
import scala.util.control.NonFatal

class Janus(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    passkeyAuthAction: ActionBuilder[UserIdentityRequest, AnyContent],
    host: String,
    stsClient: StsClient,
    configuration: Configuration,
    passkeysEnablingCookieName: String,
    passkeyAuthenticatorMetadata: Map[AAGUID, PasskeyAuthenticator],
    developerPolicyService: DeveloperPolicyFinder
      with DeveloperPolicyStatusManager,
    stage: String
)(using dynamodDB: DynamoDbClient, mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with ResultHandler
    with Logging {

  import logic.AccountOrdering.*
  import logic.UserAccess.*

  def index: Action[AnyContent] =
    authAction { implicit request =>
      val displayMode =
        Date.displayMode(ZonedDateTime.now(ZoneId.of("Europe/London")))
      (for {
        accountsAccess <- internalUserAccess(
          username(request.user),
          janusData,
          developerPolicyService.getDeveloperPolicies
        )
        userPolicyGrants = policyGrantsForUser(
          username(request.user),
          janusData.access
        )
        favourites = Favourites.fromCookie(request.cookies.get("favourites"))
        uiAccountAccess = orderedAccountAccess(
          accountsAccess,
          userPolicyGrants,
          favourites
        )

        cacheStatus = DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
          developerPolicyService.getCacheStatus,
          developerPolicyService.fetchEnabled
        )
      } yield {
        Ok(
          views.html
            .index(
              uiAccountAccess,
              cacheStatus,
              request.user,
              janusData,
              displayMode
            )
        )
      }) getOrElse Ok(views.html.noPermissions(request.user, janusData))
    }

  def admin: Action[AnyContent] =
    authAction { implicit request =>
      (for {
        accountsAccess <- adminUserAccess(
          username(request.user),
          janusData,
          developerPolicyService.getDeveloperPolicies
        )
        userPolicyGrants = policyGrantsForUser(
          username(request.user),
          janusData.admin
        )
        uiAccountAccess = orderedAccountAccess(accountsAccess, userPolicyGrants)
        cacheStatus = DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
          developerPolicyService.getCacheStatus,
          developerPolicyService.fetchEnabled
        )
      } yield {
        Ok(
          views.html.admin(
            uiAccountAccess,
            cacheStatus,
            request.user,
            janusData
          )
        )
      }) getOrElse Ok(
        views.html
          .error("You do not have admin access", Some(request.user), janusData)
      )
    }

  def support: Action[AnyContent] =
    authAction { implicit request =>
      val now = Instant.now()
      val currentSupportUsers = activeSupportUsers(now, janusData.support)
      val supportUsersInNextPeriod = nextSupportUsers(now, janusData.support)
      val currentUserFutureSupportPeriods =
        futureRotaSlotsForUser(now, janusData.support, username(request.user))
      (for {
        supportPermissions <- userSupportAccess(
          username(request.user),
          now,
          janusData.support
        )
        rawAccountAccesses = supportPermissions
          .groupBy(_.account)
          .view
          .mapValues(perms => AccountAccess(perms.toList, Nil))
          .toMap
        // support doesn't work with developer policies, so we can pass an empty set
        accountsAccess = orderedAccountAccess(rawAccountAccesses, Set.empty)
      } yield {
        Ok(
          views.html.support.support(
            accountsAccess,
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

  def userAccount: Action[AnyContent] = authAction { implicit request =>
    apiResponse {
      def dateTimeFormat(instant: Instant, formatter: DateTimeFormatter) =
        instant.atZone(ZoneId.of("Europe/London")).format(formatter)
      def dateFormat(instant: Instant) =
        dateTimeFormat(instant, DateTimeFormatter.ofPattern("d MMM yyyy"))
      def timeFormat(instant: Instant) =
        dateTimeFormat(
          instant,
          DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss XXXXX")
        )
      for {
        queryResponse <- PasskeyDB.loadCredentials(request.user)
        passkeys = PasskeyDB
          .extractMetadata(queryResponse)
          .map(p =>
            p.copy(authenticator = passkeyAuthenticatorMetadata.get(p.aaguid))
          )
      } yield views.html.userAccount(
        request.user,
        janusData,
        passkeys,
        dateFormat,
        timeFormat,
        passkeysEnablingCookieName,
        passkeysManagerLink(configuration),
        passkeysManagerLinkText(configuration)
      )
    }
  }

  def consoleLogin(permissionId: String): Action[AnyContent] =
    passkeyAuthAction { implicit request =>
      (for {
        (credentials, _) <- assumeRole(
          request.user,
          permissionId,
          JConsole,
          Customisation.durationParams(request),
          developerPolicyService.getDeveloperPolicies
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

  def consoleUrl(permissionId: String): Action[AnyContent] =
    passkeyAuthAction { implicit request =>
      (for {
        (credentials, permission) <- assumeRole(
          request.user,
          permissionId,
          JConsole,
          Customisation.durationParams(request),
          developerPolicyService.getDeveloperPolicies
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

  def credentials(permissionId: String): Action[AnyContent] =
    passkeyAuthAction { implicit request =>
      (for {
        (credentials, permission) <- assumeRole(
          request.user,
          permissionId,
          JCredentials,
          Customisation.durationParams(request),
          developerPolicyService.getDeveloperPolicies
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

  def multiCredentials(rawPermissionIds: String): Action[AnyContent] =
    passkeyAuthAction { implicit request =>
      val permissionIds = splitQuerystringParam(rawPermissionIds)
      (for {
        accountCredentials <- multiAccountAssumption(
          request.user,
          permissionIds,
          Customisation.durationParams(request),
          developerPolicyService.getDeveloperPolicies
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

  def favourite(): Action[AnyContent] =
    authAction { implicit request =>
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
      durationParams: (Option[Duration], Option[ZoneId]),
      developerPolicies: Set[DeveloperPolicy]
  ): Option[(Credentials, Permission)] = {

    // Metric dimensions
    val policyMetricDimensions: Map[String, String] = convertToDimensions(
      developerPolicies
    ) + ("permissionId" -> permissionId)
      + ("accessType" -> accessType.toString)
      + ("stage" -> stage)

    try {
      checkUserPermissionWithSource(
        username(user),
        permissionId,
        janusData,
        developerPolicies
      ) match {
        case Some(permission, accessSource) =>
          val assumeRoleResponse = invokeAssumeRole(
            user,
            permissionId,
            accessType,
            durationParams,
            permission,
            accessSource
          )
          CloudWatch.put(
            SuccessfulRequestPolicySizeMetric,
            policyMetricDimensions + ("label" -> permission.label) + ("accessSource" -> accessSource.toString),
            assumeRoleResponse.packedPolicySize()
          )
          Some(assumeRoleResponse.credentials(), permission)
        case None =>
          CloudWatch.put(
            DeniedRequest,
            policyMetricDimensions
          )
          None
      }
    } catch {
      case NonFatal(e) =>
        CloudWatch.put(
          FailedRequest,
          policyMetricDimensions
        )
        throw e
    }

  }

  private def invokeAssumeRole(
      user: UserIdentity,
      permissionId: String,
      accessType: JanusAccessType,
      durationParams: (Option[Duration], Option[ZoneId]),
      permission: Permission,
      accessSource: AccessSource
  ) = {
    val (requestedDuration, tzOffset) = durationParams

    val duration = Federation.duration(
      permission,
      requestedDuration,
      tzOffset.map(Clock.system)
    )

    val roleArn =
      Config.roleArn(permission.account.authConfigKey, configuration)

    val response = Federation.assumeRole(
      username(user),
      roleArn,
      permission,
      stsClient,
      duration
    )
    val auditLog = AuditTrail.createLog(
      user,
      permission,
      accessType,
      duration,
      janusData.access,
      accessSource == Internal
    )
    AuditTrailDB.insert(auditLog)
    logger.info(
      s"$accessType access to $permissionId granted for ${username(user)}"
    )
    response
  }

  private def convertToDimensions(
      developerPolicies: Set[DeveloperPolicy]
  ): Map[String, String] =
    developerPolicies.zipWithIndex.flatMap { case (dp, i) =>
      Map(s"account-$i" -> dp.account.name, s"grant-id-$i" -> dp.policyGrantId)
    }.toMap

  private def multiAccountAssumption(
      user: UserIdentity,
      permissionIds: List[String],
      durationParams: (Option[Duration], Option[ZoneId]),
      developerPolicies: Set[DeveloperPolicy]
  ): Option[List[(AwsAccount, Credentials)]] = {
    permissionIds
      .map(assumeRole(user, _, JCredentials, durationParams, developerPolicies))
      .map(_.map { case (credentials, permission) =>
        permission.account -> credentials
      })
      .sequence
  }

}
