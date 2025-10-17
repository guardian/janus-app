import aws.Clients
import com.gu.googleauth.AuthAction
import com.gu.play.secretrotation.*
import com.gu.play.secretrotation.aws.parameterstore
import com.gu.playpasskeyauth.models.HostApp
import com.gu.playpasskeyauth.web.{
  AuthenticationDataExtractor,
  CreationDataExtractor,
  PasskeyNameExtractor
}
import com.typesafe.config.ConfigException
import conf.Config
import controllers.*
import filters.{
  ConditionalPasskeyTransformer,
  HstsFilter,
  PasskeyAuthFilter,
  PasskeyRegistrationAuthAction,
  PasskeyRegistrationAuthFilter
}
import models.*
import models.AccountConfigStatus.*
import passkey.{ChallengeRepository, Repository}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.*
import play.api.routing.Router
import play.api.{ApplicationLoader, BuiltInComponentsFromContext, Logging, Mode}
import play.filters.HttpFiltersComponents
import play.filters.csp.CSPComponents
import router.Routes
import software.amazon.awssdk.regions.Region.EU_WEST_1
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import java.net.URI
import java.time.Duration
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

  // =====
  import com.gu.googleauth.AuthAction.UserIdentityRequest
  import com.gu.playpasskeyauth.PasskeyAuth

  private val creationDataExtractor = new CreationDataExtractor {
    def findCreationData[A](
        request: UserIdentityRequest[A]
    ): Option[JsValue] = {
      request.body match {
        case AnyContentAsFormUrlEncoded(data) =>
          data.get("passkey").flatMap(_.headOption).map(Json.parse)
        case _ => None
      }
    }
  }

  private val authenticationDataExtractor = new AuthenticationDataExtractor {
    def findAuthenticationData[A](
        request: UserIdentityRequest[A]
    ): Option[JsValue] = {
      request.body match {
        case AnyContentAsFormUrlEncoded(data) =>
          data.get("credentials").flatMap(_.headOption).map(Json.parse)
        case AnyContentAsText(data) =>
          Option(data).map(Json.parse)
        case _ => None
      }
    }
  }

  private val passkeyNameExtractor = new PasskeyNameExtractor {
    def findPasskeyName[A](request: UserIdentityRequest[A]): Option[String] = {
      request.body match {
        case AnyContentAsFormUrlEncoded(data) =>
          data.get("passkeyName").flatMap(_.headOption)
        case _ => None
      }
    }
  }

  private val passkeyAuth = new PasskeyAuth(
    app = HostApp(name = host, uri = URI.create(host)),
    authAction,
    passkeyRepo = new Repository(),
    challengeRepo = new ChallengeRepository()
  )

  private val newPasskeyController = passkeyAuth.controller(
    controllerComponents,
    creationDataExtractor,
    passkeyNameExtractor,
    registrationRedirect = routes.Janus.userAccount
  )

  private val passkeyVerificationAction =
    passkeyAuth.verificationAction(authenticationDataExtractor)

  private val conditionalPasskeyTransformer =
    new ConditionalPasskeyTransformer(
      passkeysEnabled,
      passkeysEnablingCookieName,
      authenticationDataExtractor
    )
  // =====

  private val passkeyRegistrationAuthAction = {
    new PasskeyRegistrationAuthAction(authAction, passkeyVerificationAction)
  }

  override def router: Router = new Routes(
    httpErrorHandler,
    new Janus(
      janusData,
      controllerComponents,
      authAction,
      passkeyVerificationAction,
      host,
      Clients.stsClient,
      configuration,
      passkeysEnablingCookieName,
      passkeyAuthenticatorMetadata
    ),
    new PasskeyController(
      controllerComponents,
      authAction,
      passkeyVerificationAction,
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
    newPasskeyController,
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
