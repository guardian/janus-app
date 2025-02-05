import aws.Clients
import com.gu.googleauth.AuthAction
import com.typesafe.config.ConfigException
import conf.Config
import controllers._
import filters.HstsFilter
import models._
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, EssentialFilter}
import play.api.routing.Router
import play.api.{ApplicationLoader, BuiltInComponentsFromContext, Logging}
import play.filters.HttpFiltersComponents
import router.Routes
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

class AppComponents(context: ApplicationLoader.Context)
    extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with AssetsComponents
    with HttpFiltersComponents
    with Logging {

  override def httpFilters: Seq[EssentialFilter] =
    super.httpFilters :+ new HstsFilter

  val host = Config.host(configuration)
  val googleAuthConfig = Config.googleSettings(configuration, httpConfiguration)
  val googleGroupChecker = Config.googleGroupChecker(configuration)
  val requiredGoogleGroups = Set(Config.twoFAGroup(configuration))
  val dynamodDB =
    if (context.environment.mode == play.api.Mode.Prod)
      DynamoDbClient.create()
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
    )(dynamodDB, assetsFinder),
    new Audit(janusData, controllerComponents, authAction)(
      dynamodDB,
      assetsFinder
    ),
    new RevokePermissions(
      janusData,
      controllerComponents,
      authAction,
      Clients.stsClient,
      configuration
    )(assetsFinder),
    new AuthController(
      janusData,
      controllerComponents,
      googleAuthConfig,
      googleGroupChecker,
      requiredGoogleGroups
    )(wsClient, executionContext, assetsFinder),
    new Utility(janusData, controllerComponents, authAction)(assetsFinder),
    assets
  )
}
