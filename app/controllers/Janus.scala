package controllers

import aws.{AuditTrailDB, Federation, PasskeyDB}
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
  AccountAccess,
  DeveloperPolicy,
  PasskeyAuthenticator,
  PasskeyMode
}
import play.api.mvc.*
import play.api.{Configuration, Logging, Mode}
import services.{
  DeveloperPolicyFinder,
  DeveloperPolicyStatusManager,
  MetricsService
}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.{
  Credentials,
  PackedPolicyTooLargeException
}

import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import scala.util.control.NonFatal

class Janus(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    passkeyAuthAction: ActionBuilder[UserIdentityRequest, AnyContent],
    host: String,
    stsClient: StsClient,
    configuration: Configuration,
    passkeyMode: PasskeyMode,
    passkeyAuthenticatorMetadata: Map[AAGUID, PasskeyAuthenticator],
    developerPolicyService: DeveloperPolicyFinder
      with DeveloperPolicyStatusManager,
    metricsService: MetricsService
)(using dynamoDB: DynamoDbClient, mode: Mode, assetsFinder: AssetsFinder)
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
          request.user,
          janusData,
          developerPolicyService.getDeveloperPolicies
        )
        userPolicyGrants = policyGrantsForUser(
          request.user,
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

        mfaInUse = PasskeyDB.hasPasskey(request.user).toOption.getOrElse(false)

        passkeyNotRequired = Config.passkeyMode(
          configuration
        ) != PasskeyMode.Required
      } yield {
        if (mfaInUse || passkeyNotRequired)
          val mfaRequiredDateMaybe = Config.mfaRequiredDataMaybe(configuration)
          val mfaComing = !mfaInUse && mfaRequiredDateMaybe
            .exists(d => LocalDate.now().isBefore(d))
          val mfaDaysRemaining = mfaRequiredDateMaybe
            .map(d => ChronoUnit.DAYS.between(LocalDate.now(), d))
            .getOrElse(0L)
          val mfaDisplayDate = mfaRequiredDateMaybe
            .map(d =>
              d.format(
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
              )
            )
            .getOrElse("today")
          Ok(
            views.html
              .index(
                uiAccountAccess,
                cacheStatus,
                request.user,
                janusData,
                displayMode,
                mfaComing,
                mfaDaysRemaining,
                mfaDisplayDate
              )
          )
        else SeeOther(controllers.routes.Janus.userAccount.url)
      }) getOrElse Ok(views.html.noPermissions(request.user, janusData))
    }

  def superuser: Action[AnyContent] =
    authAction { implicit request =>
      (for {
        accountsAccess <- superuserAccess(
          request.user,
          janusData,
          developerPolicyService.getDeveloperPolicies
        )
        userPolicyGrants = policyGrantsForUser(
          request.user,
          janusData.superuser
        )
        uiAccountAccess = orderedAccountAccess(accountsAccess, userPolicyGrants)
        cacheStatus = DeveloperPolicies.lookupDeveloperPolicyCacheStatus(
          developerPolicyService.getCacheStatus,
          developerPolicyService.fetchEnabled
        )
      } yield {
        Ok(
          views.html.superuser(
            uiAccountAccess,
            cacheStatus,
            request.user,
            janusData
          )
        )
      }) getOrElse Ok(
        views.html
          .error(
            "You do not have superuser access",
            Some(request.user),
            janusData
          )
      )
    }

  def support: Action[AnyContent] =
    authAction { implicit request =>
      val now = Instant.now()
      val currentSupportUsers = activeSupportUsers(now, janusData.support)
      val supportUsersInNextPeriod = nextSupportUsers(now, janusData.support)
      val currentUserFutureSupportPeriods =
        futureRotaSlotsForUser(now, janusData.support, request.user)
      (for {
        supportPermissions <- userSupportAccess(
          request.user,
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
        passkeyMode,
        passkeysManagerLink(configuration),
        passkeysManagerLinkText(configuration)
      )
    }
  }

  /** Shows a page that lets a user build a `/consoleRedirect` URL for one of
    * their permissions, to be shared in docs/runbooks (see
    * [[consoleRedirect]]).
    */
  def consoleRedirectBuilder: Action[AnyContent] =
    authAction { implicit request =>
      (for {
        accountsAccess <- internalUserAccess(
          request.user,
          janusData,
          developerPolicyService.getDeveloperPolicies
        )
        userPolicyGrants = policyGrantsForUser(request.user, janusData.access)
        uiAccountAccess = orderedAccountAccess(accountsAccess, userPolicyGrants)
      } yield {
        Ok(
          views.html.consoleRedirectBuilder(
            uiAccountAccess,
            request.user,
            janusData
          )
        )
      }) getOrElse Ok(views.html.noPermissions(request.user, janusData))
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
          s"console url login to $permissionId denied for ${username(request.user)}"
        )
        Forbidden(views.html.permissionDenied(request.user, janusData))
      }
    }

  /** Shows an interstitial confirmation page for a `/consoleRedirect` link
    * (generated via [[consoleRedirectBuilder]]), before any sign-in happens.
    *
    * This lets the user see exactly where the link will send them and which
    * permission it will use, so they can spot a suspicious/incorrect
    * destination before following it, and check they actually have the
    * permission being requested. From here they can either sign in to a new
    * Janus session and be redirected, or - if they already have a console
    * session open for the target account - skip straight to the destination
    * without disturbing that session.
    */
  def consoleRedirectConfirm(
      permissionId: String,
      destination: String
  ): Action[AnyContent] =
    authAction { implicit request =>
      val destinationValid = Federation.isValidConsoleDestination(destination)
      val accessiblePermission = checkUserPermissionWithSource(
        request.user,
        permissionId,
        Instant.now(),
        janusData,
        developerPolicyService.getDeveloperPolicies
      ).map { case (permission, _, _) => permission }
      // Permission may be known even if this user doesn't personally have
      // access to it (so we can still show its description/account below).
      // Developer-policy-derived permissions aren't included here, as they
      // can only be looked up for a specific user.
      val knownPermission =
        accessiblePermission.orElse(
          Permission.allPermissions(janusData).find(_.id == permissionId)
        )
      Ok(
        views.html.consoleRedirectConfirm(
          permissionId,
          destination,
          destinationValid,
          knownPermission,
          hasAccess = accessiblePermission.isDefined,
          request.user,
          janusData
        )
      )
    }

  /** Signs the user in to the AWS console for the given permission, then
    * redirects them straight to the given `destination` (e.g. a deep link to a
    * specific S3 bucket). This lets docs/runbooks link directly to an AWS
    * console page without requiring the reader to separately sign in to
    * Janus/AWS first.
    */
  def consoleRedirect(
      permissionId: String,
      destination: String
  ): Action[AnyContent] =
    passkeyAuthAction { implicit request =>
      if (!Federation.isValidConsoleDestination(destination)) {
        logger.warn(
          s"console redirect to $permissionId denied for ${username(request.user)}: invalid destination '$destination'"
        )
        BadRequest(
          views.html.error(
            "Invalid redirect destination",
            Some(request.user),
            janusData
          )
        )
      } else {
        (for {
          (credentials, _) <- assumeRole(
            request.user,
            permissionId,
            JConsole,
            Customisation.durationParams(request),
            developerPolicyService.getDeveloperPolicies
          )
          loginUrl = Federation.generateLoginUrl(credentials, host, destination)
        } yield {
          SeeOther(loginUrl)
            .withHeaders(CACHE_CONTROL -> "no-cache")
        }) getOrElse {
          logger.warn(
            s"console redirect to $permissionId denied for ${username(request.user)}"
          )
          Forbidden(views.html.permissionDenied(request.user, janusData))
        }
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
          s"denied multi credentials to $rawPermissionIds for ${username(request.user)}"
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
    checkUserPermissionWithSource(
      user,
      permissionId,
      Instant.now(),
      janusData,
      developerPolicies
    ) match {
      case Some(permission, accessSource, permissionType) =>
        try {
          val (requestedDuration, tzOffset) = durationParams

          val duration = Federation.duration(
            permission,
            requestedDuration,
            tzOffset.map(Clock.system)
          )

          val roleArn =
            Config.roleArn(permission.account.authConfigKey, configuration)

          val (credentials, size) = Federation.assumeRole(
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
            accessSource == Internal,
            permissionType
          )
          AuditTrailDB.insert(auditLog)
          logger.info(
            s"$accessType access to $permissionId granted for ${username(user)}"
          )
          metricsService.putSuccessfulRequest(
            permissionId,
            permission.label,
            accessType,
            accessSource,
            permissionType,
            size
          )
          Some((credentials, permission))
        } catch {
          case e: PackedPolicyTooLargeException =>
            logger.error("Exception creating credentials", e)
            metricsService.putTooLargeRequest(
              permissionId,
              permission.label,
              accessType,
              accessSource,
              permissionType,
              e
            )
            throw e
          case NonFatal(e) =>
            logger.error("Exception creating credentials", e)
            metricsService.putFailedRequest(
              permissionId,
              permission.label,
              accessType,
              accessSource,
              permissionType
            )
            throw e
        }
      case None =>
        metricsService.putDeniedRequest(
          permissionId,
          accessType
        )
        None
    }
  }

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
