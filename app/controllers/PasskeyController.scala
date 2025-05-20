package controllers

import aws.PasskeyChallengeDB.UserChallenge
import aws.{PasskeyChallengeDB, PasskeyDB}
import com.gu.googleauth.AuthAction
import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.janus.model.JanusData
import constants.ValidationConstants
import logic.AccountOrdering.orderedAccountAccess
import logic.UserAccess.{userAccess, username}
import logic.{Date, Favourites, Passkey}
import models.JanusException
import models.JanusException.throwableWrites
import models.PasskeyEncodings._
import play.api.http.Writeable
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import play.api.mvc._
import play.api.{Logging, Mode}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}
import scala.concurrent.ExecutionContext
import scala.language.implicitConversions
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

  // Use the constants directly from Scala
  private val validPasskeyNameRegex =
    ValidationConstants.PasskeyName.REGEX_PATTERN.r
  private val maxPasskeyNameLength = ValidationConstants.PasskeyName.MAX_LENGTH

  /** Validates a passkey name against format and length requirements
    * @param name
    *   The passkey name to validate
    * @return
    *   True if the name is valid, false otherwise
    */
  private def isValidPasskeyName(name: String): Boolean = {
    name.nonEmpty &&
    name.length <= maxPasskeyNameLength &&
    validPasskeyNameRegex.matches(name)
  }

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
  )(implicit resultConverter: A => Result): Result =
    action match {
      case Failure(err: JanusException) =>
        logger.error(err.engineerMessage, err.causedBy.orNull)
        Status(err.httpCode)(toJson(err))
      case Failure(err) =>
        logger.error(err.getMessage, err)
        Status(INTERNAL_SERVER_ERROR)(toJson(err))
      case Success(a) => resultConverter(a)
    }

  implicit def jsonToResult[A](a: A)(implicit writes: Writes[A]): Result = Ok(
    toJson(a)
  )

  implicit def htmlToResult[A](a: A)(implicit writeable: Writeable[A]): Result =
    Ok(a)

  implicit def resultToResult(r: Result): Result = r

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
          case Some(values) => Success(values.head)
          case None =>
            Failure(
              JanusException.missingFieldInRequest(request.user, "passkey")
            )
        }
        passkeyName <- request.body.get("passkeyName") match {
          case Some(values) => Success(values.head)
          case None =>
            Failure(
              JanusException.missingFieldInRequest(request.user, "passkeyName")
            )
        }

        // Validate the passkey name format
        _ <-
          if (!isValidPasskeyName(passkeyName)) {
            Failure(
              JanusException.invalidFieldInRequest(
                request.user,
                "passkeyName",
                new IllegalArgumentException(
                  "Passkey name must contain only letters, numbers, spaces, underscores, and hyphens, and be under 50 characters"
                )
              )
            )
          } else Success(())

        // Check for duplicate passkey names
        existingPasskeys <- PasskeyDB.loadCredentials(request.user)
        _ <-
          if (
            PasskeyDB
              .extractMetadata(existingPasskeys)
              .exists(_.name.equalsIgnoreCase(passkeyName))
          ) {
            Failure(
              JanusException.invalidFieldInRequest(
                request.user,
                "passkeyName",
                new IllegalArgumentException(
                  s"A passkey with name '$passkeyName' already exists for this user"
                )
              )
            )
          } else Success(())

        credRecord <- Passkey.verifiedRegistration(
          host,
          request.user,
          challenge,
          passkey
        )
        _ <- PasskeyDB.insert(request.user, credRecord, passkeyName)
        _ <- PasskeyChallengeDB.delete(request.user)
        _ = logger.info(
          s"Registered passkey '$passkeyName' for user ${request.user.username}"
        )
      } yield Redirect("/user-account")
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

  /** Deletes a passkey for a user */
  def deletePasskey: Action[Map[String, Seq[String]]] = authAction(
    parse.formUrlEncoded
  ) { implicit request =>
    apiResponse(
      for {
        passkeyId <- request.body.get("passkeyId") match {
          case Some(values) => Success(values.head)
          case None =>
            Failure(
              JanusException.missingFieldInRequest(request.user, "passkeyId")
            )
        }
        _ <- PasskeyDB.deleteById(request.user, passkeyId)
        _ = logger.info(s"Deleted passkey for user ${request.user.username}")
      } yield Redirect("/user-account")
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

  // TODO: move to Janus or account controller
  def showUserAccountPage: Action[AnyContent] = authAction { implicit request =>
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
        passkeys = PasskeyDB.extractMetadata(queryResponse)
      } yield views.html.userAccount(
        request.user,
        janusData,
        passkeys,
        dateFormat,
        timeFormat
      )
    }
  }
}
