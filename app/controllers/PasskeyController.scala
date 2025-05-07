package controllers

import aws.PasskeyChallengeDB.UserChallenge
import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.AuthAction
import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.janus.model.JanusData
import logic.AccountOrdering.orderedAccountAccess
import logic.UserAccess.{userAccess, username}
import logic.{Date, Favourites, Passkey}
import models.JanusException
import models.JanusException.throwableWrites
import models.Passkey._
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import play.api.mvc._
import play.api.{Logging, Mode}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import java.time.{ZoneId, ZonedDateTime}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/** Controller for handling passkey registration and authentication. */
class PasskeyController(
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    host: String,
    janusData: JanusData
)(implicit
    dynamoDb: DynamoDbClient,
    mode: Mode,
    assetsFinder: AssetsFinder,
    ec: ExecutionContext
) extends AbstractController(controllerComponents)
    with Logging {

  // To handle API responses with no return value
  implicit val unitWrites: Writes[Unit] =
    Writes(_ => Json.obj("status" -> "success"))

  private def passkeyAuthAction
      : ActionBuilder[UserIdentityRequest, AnyContent] =
    authAction.andThen(new PasskeyAuthFilter(host))

  private val appName = mode match {
    case Mode.Dev  => "Janus-Dev"
    case Mode.Test => "Janus-Test"
    case Mode.Prod => "Janus-Prod"
  }

  private def apiResponse[A](
      action: => Try[A]
  )(implicit writes: Writes[A]): Result =
    action match {
      case Failure(err: JanusException) =>
        logger.error(err.engineerMessage, err.causedBy.orNull)
        Status(err.httpCode)(toJson(err))
      case Failure(err) =>
        logger.error(err.getMessage, err)
        Status(INTERNAL_SERVER_ERROR)(toJson(err))
      case Success(a) => Ok(toJson(a))
    }

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#generating-a-webauthn-credential-key-pair]].
    */
  def registrationOptions: Action[Unit] = authAction(parse.empty) { request =>
    apiResponse(
      for {
        options <- Passkey.registrationOptions(appName, host, request.user)
        _ <- PasskeyChallengeDB.insert(
          UserChallenge(request.user, options.getChallenge)
        )
        _ = logger.info(
          s"Created registration options for user ${request.user.username}"
        )
      } yield options
    )
  }

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#registering-the-webauthn-public-key-credential-on-the-server]].
    */
  def register: Action[Map[String, Seq[String]]] = authAction(
    parse.formUrlEncoded
  ) { request =>
    apiResponse(
      for {
        challengeResponse <- PasskeyChallengeDB.loadChallenge(request.user)
        challenge <- PasskeyChallengeDB.extractChallenge(
          challengeResponse,
          request.user
        )
        passkey <- request.body.get("passkey") match {
          case Some(a) => Success(a.head)
          case None =>
            Failure(
              JanusException(
                "Missing passkey",
                "Missing passkey in request body",
                400,
                None
              )
            )
        }
        passkeyName <- request.body.get("passkeyName") match {
          case Some(a) => Success(a.head)
          case None =>
            Failure(
              JanusException(
                "Missing passkey name",
                "Missing passkey name in request body",
                400,
                None
              )
            )
        }
        credRecord <- Passkey.verifiedRegistration(host, challenge, passkey)
        _ <- PasskeyDB.insert(request.user, credRecord, passkeyName)
        _ <- PasskeyChallengeDB.delete(request.user)
        _ = logger.info(s"Registered passkey for user ${request.user.username}")
      } yield ()
    )
  }

  /** See
    * [[https://webauthn4j.github.io/webauthn4j/en/#generating-a-webauthn-assertion]].
    */
  def authenticationOptions: Action[Unit] = authAction(parse.empty) { request =>
    apiResponse(
      for {
        options <- Passkey.authenticationOptions(request.user)
        _ <- PasskeyChallengeDB.insert(
          UserChallenge(request.user, options.getChallenge)
        )
        _ = logger.info(
          s"Created authentication options for user ${request.user.username}"
        )
      } yield options
    )
  }

  // To be removed when passkeyAuthAction has been applied to real endpoints
  def protectedCredentialsPage: Action[AnyContent] = passkeyAuthAction { _ =>
    Ok("This is the protected page you're authorised to see.")
  }

  // To be removed when passkeyAuthAction has been applied to real endpoints
  def pretendAwsConsole: Action[AnyContent] = Action {
    Ok("This is the pretend AWS console.")
  }

  // To be removed when passkeyAuthAction has been applied to real endpoints
  def protectedRedirect: Action[AnyContent] = passkeyAuthAction { _ =>
    Redirect("/passkey/pretend-aws-console")
  }

  // To be removed when passkeyAuthAction has been applied to real endpoints
  def mockHome: Action[AnyContent] = authAction { implicit request =>
    val displayMode =
      Date.displayMode(ZonedDateTime.now(ZoneId.of("Europe/London")))
    (for {
      permissions <- userAccess(username(request.user), janusData.access)
      favourites = Favourites.fromCookie(request.cookies.get("favourites"))
      awsAccountAccess = orderedAccountAccess(permissions, favourites)
    } yield {
      Ok(
        views.html.passkeymock.index(
          awsAccountAccess,
          request.user,
          janusData,
          displayMode
        )
      )
    }).getOrElse(Ok(views.html.noPermissions(request.user, janusData)))
  }

  def showUserAccountPage: Action[AnyContent] = authAction { implicit request =>
    Ok(views.html.userAccount(request.user, janusData))
  }
}
