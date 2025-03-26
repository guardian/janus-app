import aws.Clients
import com.gu.googleauth.AuthAction
import com.gu.play.secretrotation._
import com.gu.play.secretrotation.aws.parameterstore
import com.typesafe.config.ConfigException
import conf.Config
import controllers._
import filters.HstsFilter
import models._
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, EssentialFilter}
import play.api.routing.Router
import play.api.{ApplicationLoader, BuiltInComponentsFromContext, Logging, Mode}
import play.filters.HttpFiltersComponents
import router.Routes
import software.amazon.awssdk.regions.Region.EU_WEST_1
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import java.time.Duration

class AppComponents(context: ApplicationLoader.Context)
    extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with AssetsComponents
    with HttpFiltersComponents
    with RotatingSecretComponents
    with Logging {

  override def httpFilters: Seq[EssentialFilter] =
    super.httpFilters :+ new HstsFilter

  // used by the template to detect development environment
  // in that situation, it'll load assets directly from npm vs production, where they'll come from the bundled files
  val mode: Mode = context.environment.mode

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
  val dynamodDB =
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

  override def router: Router = new Routes(
    httpErrorHandler,
    new Janus(
      janusData,
      controllerComponents,
      authAction,
      host,
      Clients.stsClient,
      configuration
    )(dynamodDB, mode, assetsFinder),
    new Audit(janusData, controllerComponents, authAction)(
      dynamodDB,
      mode,
      assetsFinder
    ),
    new RevokePermissions(
      janusData,
      controllerComponents,
      authAction,
      Clients.stsClient,
      configuration
    )(mode, assetsFinder),
    new AuthController(
      janusData,
      controllerComponents,
      googleAuthConfig,
      googleGroupChecker,
      requiredGoogleGroups
    )(wsClient, executionContext, mode, assetsFinder),
    new PasskeyController(controllerComponents, authAction, host, janusData)(
      mode,
      assetsFinder
    ),
    new Utility(janusData, controllerComponents, authAction, configuration)(
      mode,
      assetsFinder
    ),
    assets
  )
}
