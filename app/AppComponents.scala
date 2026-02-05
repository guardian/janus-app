import aws.Clients
import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.play.secretrotation.*
import com.gu.play.secretrotation.aws.parameterstore
import com.gu.playpasskeyauth.PasskeyAuth
import com.gu.playpasskeyauth.models.{HostApp, UserId, UserIdExtractor}
import com.gu.playpasskeyauth.web.*
import com.typesafe.config.ConfigException
import conf.Config
import controllers.*
import filters.*
import models.*
import models.AccountConfigStatus.*
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.*
import play.api.routing.Router
import play.api.{ApplicationLoader, BuiltInComponentsFromContext, Logging, Mode}
import play.filters.HttpFiltersComponents
import play.filters.csp.CSPComponents
import router.Routes
import services.{
  DynamoPasskeyChallengeRepository,
  DynamoPasskeyRepository,
  ProvisionedRoleCachingService
}
import software.amazon.awssdk.regions.Region.EU_WEST_1
import software.amazon.awssdk.services.dynamodb.{
  DynamoDbAsyncClient,
  DynamoDbClient
}

import java.net.URI
import java.time.{Clock, Duration}
import scala.concurrent.ExecutionContext
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

  val dynamoDbAsync: DynamoDbAsyncClient =
    if (context.environment.mode == play.api.Mode.Prod)
      Clients.dynamoDbAsync
    else Clients.localDbAsync

  given ExecutionContext = executionContext

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

  private val provisionedRoleCachingService =
    ProvisionedRoleCachingService.start(
      applicationLifecycle,
      accounts = janusData.accounts,
      config = configuration,
      sts = Clients.stsClient
    )

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

  val userExtractor: UserExtractor[UserIdentity, UserIdentityRequest] =
    new UserExtractor[UserIdentity, UserIdentityRequest] {
      def extractUser[A](request: UserIdentityRequest[A]): UserIdentity =
        request.user
    }

  given UserIdExtractor[UserIdentity] = user => UserId(user.username)

  private val creationDataExtractor =
    new CreationDataExtractor[[A] =>> RequestWithUser[UserIdentity, A]] {
      override def findCreationData[A](
          request: RequestWithUser[UserIdentity, A]
      ): Option[JsValue] =
        request.body match {
          case AnyContentAsFormUrlEncoded(data) =>
            data.get("passkey").flatMap(_.headOption).map(Json.parse)
          case _ => None
        }
    }

  private val authenticationDataExtractor =
    new AuthenticationDataExtractor[[A] =>> RequestWithUser[UserIdentity, A]] {
      override def findAuthenticationData[A](
          request: RequestWithUser[UserIdentity, A]
      ): Option[JsValue] =
        request.body match {
          case AnyContentAsFormUrlEncoded(data) =>
            data.get("credentials").flatMap(_.headOption).map(Json.parse)
          case AnyContentAsText(data) =>
            Option(data).map(Json.parse)
          case _ => None
        }
    }

  private val passkeyNameExtractor =
    new PasskeyNameExtractor[[A] =>> RequestWithUser[UserIdentity, A]] {
      override def findPasskeyName[A](
          request: RequestWithUser[UserIdentity, A]
      ): Option[String] =
        request.body match {
          case AnyContentAsFormUrlEncoded(data) =>
            data.get("passkeyName").flatMap(_.headOption)
          case _ => None
        }
    }

  private val passkeyRepo =
    new DynamoPasskeyRepository(dynamoDbAsync, Clock.systemUTC())

  private val challengeRepo = new DynamoPasskeyChallengeRepository(
    dynamoDbAsync
  )

  private val passkeyAuth = new PasskeyAuth[UserIdentity, AnyContent](
    controllerComponents,
    app = HostApp(name = host, uri = URI.create(host)),
    userAction = authAction.andThen(new UserAction(userExtractor)),
    passkeyRepo,
    challengeRepo,
    creationDataExtractor,
    authenticationDataExtractor,
    passkeyNameExtractor,
    registrationRedirect = routes.Janus.userAccount
  )

//  private val passkeyAuth = PasskeyAuthSimple(
//      appName = host,
//      appOrigin = URI.create(host),
//    passkeyRepo,
//    challengeRepo = new DynamoPasskeyChallengeRepository(dynamoDbAsync)
//  )

//  private val passkeyVerificationAction =
//    new ConditionalPasskeyVerificationAction(
//      passkeysEnabled,
//      passkeysEnablingCookieName,
//      authAction,
//      passkeyAuth.verificationAction()
//    )

  override def router: Router = new Routes(
    httpErrorHandler,
    // TODO
    new Janus(
      janusData,
      controllerComponents,
      authAction,
      passkeyAuth.verificationAction(),
      host,
      Clients.stsClient,
      passkeyDb = passkeyRepo,
      configuration,
      passkeysEnablingCookieName,
      passkeyAuthenticatorMetadata
    )(using ???, ???, ???, ???),
    new PasskeyController(
      controllerComponents,
      authAction,
      passkeyAuth,
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
    new AccountsController(
      janusData,
      controllerComponents,
      authAction,
      configuration,
      provisionedRoleCachingService
    ),
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
