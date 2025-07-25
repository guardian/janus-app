import aws.{Clients, PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.play.secretrotation.*
import com.gu.play.secretrotation.aws.parameterstore
import com.gu.playpasskeyauth.models.HostApp
import com.gu.playpasskeyauth.services.{
  PasskeyChallengeRepository,
  PasskeyRepository,
  PasskeyVerificationServiceImpl
}
import com.typesafe.config.ConfigException
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.data.AuthenticationData
import com.webauthn4j.data.client.challenge.Challenge
import conf.Config
import controllers.*
import filters.{HstsFilter, PasskeyAuthFilter, PasskeyRegistrationAuthFilter}
import models.*
import models.AccountConfigStatus.*
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, EssentialFilter}
import play.api.routing.Router
import play.api.{ApplicationLoader, BuiltInComponentsFromContext, Logging, Mode}
import play.filters.HttpFiltersComponents
import play.filters.csp.CSPComponents
import router.Routes
import software.amazon.awssdk.regions.Region.EU_WEST_1
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import java.net.URI
import java.time.Duration
import scala.concurrent.Future
import scala.util.Success
import scala.util.chaining.scalaUtilChainingOps

class AppComponents(context: ApplicationLoader.Context)
    extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with AssetsComponents
    with HttpFiltersComponents
    with CSPComponents
    with RotatingSecretComponents
    with Logging {

  given WSClient = wsClient
  given AssetsFinder = assetsFinder

  override def httpFilters: Seq[EssentialFilter] =
    super.httpFilters :+ cspFilter :+ new HstsFilter

  // used by the template to detect development environment
  // in that situation, it'll load assets directly from npm vs production, where they'll come from the bundled files
  given mode: Mode = context.environment.mode

  // Janus has no Code stage
  private val stage = mode match {
    case Mode.Prod => "PROD"
    case _         => "DEV"
  }

  // Reads Play secret from SSM
  val secretStateSupplier: SnapshotProvider =
    new parameterstore.SecretSupplier(
      TransitionTiming(
        // When a new secret value is read it isn't used immediately, to keep all EC2 instances in sync.  The new value is used after the usageDelay has passed.
        usageDelay = Duration.ofMinutes(3),
        // Old secret values are still respected for an overlapDuration.
        overlapDuration = Duration.ofHours(2)
      ),
      s"/$stage/security/janus/play.http.secret.key",
      parameterstore.AwsSdkV2(Clients.ssm)
    )

  val host = Config.host(configuration)
  val googleAuthConfig =
    Config.googleSettings(configuration, secretStateSupplier)
  val googleGroupChecker = Config.googleGroupChecker(configuration)
  val requiredGoogleGroups = Set(Config.twoFAGroup(configuration))
  given dynamodDB: DynamoDbClient =
    if (context.environment.mode == play.api.Mode.Prod)
      DynamoDbClient.builder().region(EU_WEST_1).build()
    else Clients.localDb

  val janusData = Config.janusData(configuration)

  Config.validateAccountConfig(janusData, configuration) match {
    case FederationConfigError(causedBy) =>
      throw new ConfigException.Missing("federation", causedBy)
    case ConfigWarn(accounts) =>
      logger.warn(
        s"Account(s) present in config that are not present in the JanusData: ${accounts.mkString(", ")}"
      )
    case ConfigError(accounts) =>
      throw new RuntimeException(
        s"One or more accounts are missing from config: ${accounts.mkString(", ")}"
      )
    case ConfigSuccess =>
  }

  val authAction = new AuthAction[AnyContent](
    googleAuthConfig,
    routes.AuthController.login,
    controllerComponents.parsers.default
  )(executionContext)

  private val passkeyAuthenticatorMetadata =
    PasskeyAuthenticator.fromResource(
      "passkeys_aaguid_descriptions.json"
    )

  private val passkeysEnabled: Boolean =
    configuration
      .get[Boolean]("passkeys.enabled")
      .tap(enabled =>
        if !enabled then
          logger.warn("Passkey authentication is globally disabled!")
      )
  private val passkeysEnablingCookieName: String =
    configuration.get[String]("passkeys.enablingCookieName")

  private val passkeyAuthFilter =
    new PasskeyAuthFilter(
      host,
      passkeysEnabled,
      passkeysEnablingCookieName
    )
  private val passkeyRegistrationAuthFilter =
    new PasskeyRegistrationAuthFilter(passkeyAuthFilter)

  private val passkeyAuthAction = authAction.andThen(passkeyAuthFilter)
  private val passkeyRegistrationAuthAction =
    authAction.andThen(passkeyRegistrationAuthFilter)

  private val pkRepo = new PasskeyRepository {
    override def loadCredentialRecord(
        userId: String,
        passkeyId: Array[Byte]
    ): Future[Option[CredentialRecord]] = {
      val userIdentity = UserIdentity(
        sub = userId,
        email = userId,
        firstName = userId,
        lastName = "",
        exp = 0L,
        avatarUrl = None
      )
      Future.fromTry(
        PasskeyDB.loadCredential(userIdentity, passkeyId).flatMap { response =>
          if (response.hasItem) {
            PasskeyDB.extractCredential(response, userIdentity).map(Some(_))
          } else {
            Success(None)
          }
        }
      )
    }

    override def loadPasskeyIds(userId: String): Future[List[String]] = {
      val userIdentity = UserIdentity(
        sub = userId,
        email = userId,
        firstName = userId,
        lastName = "",
        exp = 0L,
        avatarUrl = None
      )
      Future.fromTry(
        PasskeyDB.loadCredentials(userIdentity).map { response =>
          PasskeyDB.extractMetadata(response).map(_.id).toList
        }
      )
    }

    override def updateAuthenticationCounter(
        userId: String,
        authData: AuthenticationData
    ): Future[Unit] = {
      val userIdentity = UserIdentity(
        sub = userId,
        email = userId,
        firstName = userId,
        lastName = "",
        exp = 0L,
        avatarUrl = None
      )
      Future.fromTry(PasskeyDB.updateCounter(userIdentity, authData))
    }

    override def updateLastUsedTime(
        userId: String,
        authData: AuthenticationData
    ): Future[Unit] = {
      val userIdentity = UserIdentity(
        sub = userId,
        email = userId,
        firstName = userId,
        lastName = "",
        exp = 0L,
        avatarUrl = None
      )
      Future.fromTry(PasskeyDB.updateLastUsedTime(userIdentity, authData))
    }
  }

  private val pkChallRepo = new PasskeyChallengeRepository {
    override def loadAuthenticationChallenge(
        userId: String
    ): Future[Option[Challenge]] = {
      val userIdentity = UserIdentity(
        sub = userId,
        email = userId,
        firstName = userId,
        lastName = "",
        exp = 0L,
        avatarUrl = None
      )
      Future.fromTry(
        PasskeyChallengeDB
          .loadChallenge(userIdentity, PasskeyFlow.Authentication)
          .flatMap { response =>
            if (response.hasItem) {
              PasskeyChallengeDB
                .extractChallenge(response, userIdentity)
                .map(Some(_))
            } else {
              Success(None)
            }
          }
      )
    }

    override def deleteAuthenticationChallenge(userId: String): Future[Unit] = {
      val userIdentity = UserIdentity(
        sub = userId,
        email = userId,
        firstName = userId,
        lastName = "",
        exp = 0L,
        avatarUrl = None
      )
      Future.fromTry(
        PasskeyChallengeDB.delete(userIdentity, PasskeyFlow.Authentication)
      )
    }
  }

  private val hostApp = HostApp(
    name = mode match {
      case Mode.Dev  => "Janus-Dev"
      case Mode.Test => "Janus-Test"
      case Mode.Prod => "Janus-Prod"
    },
    uri = URI.create(host)
  )

  private val pkService =
    new PasskeyVerificationServiceImpl(
      app = hostApp,
      passkeyRepo = pkRepo,
      challengeRepo = pkChallRepo
    )

  override def router: Router = new Routes(
    httpErrorHandler,
    new Janus(
      janusData,
      controllerComponents,
      authAction,
      passkeyAuthAction,
      host,
      Clients.stsClient,
      configuration,
      passkeysEnablingCookieName,
      passkeyAuthenticatorMetadata
    ),
    new PasskeyController(
      controllerComponents,
      authAction,
      passkeyAuthAction,
      passkeyRegistrationAuthAction,
      host,
      janusData,
      passkeysEnabled,
      passkeysEnablingCookieName
    ),
    new Audit(janusData, controllerComponents, authAction),
    new RevokePermissions(
      janusData,
      controllerComponents,
      authAction,
      Clients.stsClient,
      configuration
    ),
    new AuthController(
      janusData,
      controllerComponents,
      googleAuthConfig,
      googleGroupChecker,
      requiredGoogleGroups
    ),
    new PasskeyController(
      controllerComponents,
      authAction,
      passkeyAuthAction,
      passkeyRegistrationAuthAction,
      host,
      janusData
    ),
    new ConcretePasskeyController(controllerComponents, authAction, pkService),
    new Utility(
      janusData,
      controllerComponents,
      authAction,
      configuration,
      passkeysEnablingCookieName
    ),
    assets
  )
}
