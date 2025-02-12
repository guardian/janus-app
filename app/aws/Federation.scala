package aws

import awscala.iam._
import com.amazonaws.services.identitymanagement.model.GetRoleRequest
import com.gu.janus.model.{AwsAccount, Permission}
import data.Policies
import logic.Date
import org.joda.time.{DateTime, DateTimeZone, Duration, Period}
import play.api.libs.json.Json
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.{
  AssumeRoleRequest,
  Credentials
}

import java.net.{URI, URLEncoder}
import java.nio.charset.StandardCharsets.UTF_8
import scala.io.Source

object Federation {

  /** Credential/Console lease times. Defaults are used when user doesn't
    * request specific time periods, max will limit how long can be requested.
    */
  val maxShortTime: Duration = 3.hours
  val minShortTime: Duration = 15.minutes
  val defaultShortTime: Duration = 1.hour
  val maxLongTime: Duration = 10.hours
  val minLongTime: Duration = 1.hour
  val defaultLongTime: Duration = 8.hours

  private val awsMinimumSessionLength = 900.seconds

  private val signInUrl = "https://signin.aws.amazon.com/federation"
  private val consoleUrl = "https://console.aws.amazon.com/"

  /** Calculates the duration of a login session.
    *
    * Users can request a specific length of time (up to the max), or use the
    * default, which may depend on their timezone.
    *
    * The tests explain the different cases, this is a tricky function.
    */
  def duration(
      permission: Permission,
      requestedSeconds: Option[Duration] = None,
      timezone: Option[DateTimeZone] = None
  ): Duration = {
    if (permission.shortTerm) {
      // short term permission, give them requested or default (limited by max)
      val calculated = requestedSeconds.fold(defaultShortTime) { requested =>
        Date.minDuration(requested, maxShortTime)
      }
      Date.maxDuration(calculated, minShortTime)
    } else {
      // use requested or default (limited by max)
      // if nothing is requested, try to give them until 19:00 local time (requires timezone)
      val calculated = requestedSeconds match {
        case None =>
          timezone.fold(defaultLongTime) { tz =>
            val localEndOfWork = {
              val withTime = DateTime.now(tz).withTime(19, 0, 0, 0)
              if (withTime.isBefore(DateTime.now(tz))) withTime.plusDays(1)
              else withTime
            }
            val durationToEndOfWork =
              new Duration(DateTime.now(tz), localEndOfWork)
            if (durationToEndOfWork.isShorterThan(maxLongTime))
              durationToEndOfWork
            else defaultLongTime
          }
        case Some(requested) =>
          Date.minDuration(requested, maxLongTime)
      }
      Date.maxDuration(calculated, minLongTime)
    }
  }

  def assumeRole(
      username: String,
      roleArn: String,
      permission: Permission,
      sts: StsClient,
      duration: Duration
  ): Credentials = {
    val request = AssumeRoleRequest
      .builder()
      .roleArn(roleArn)
      .roleSessionName(username)
      .policy(permission.policy)
      .durationSeconds(duration.getStandardSeconds.toInt)
      .build()
    val response = sts.assumeRole(request)
    response.credentials()
  }

  def generateLoginUrl(
      temporaryCredentials: Credentials,
      host: String,
      autoLogout: Boolean
  ): String = {
    // See https://github.com/seratch/AWScala/blob/5d9012dec25eafc4275765bfc5cbe46c3ed37ba2/sts/src/main/scala/awscala/sts/STS.scala
    val token = URLEncoder.encode(signinToken(temporaryCredentials), UTF_8)
    val issuer = URLEncoder.encode(host, UTF_8)
    val destination = URLEncoder.encode(consoleUrl, UTF_8)
    val url =
      s"$signInUrl?Action=login&SigninToken=$token&Issuer=$issuer&Destination=$destination"
    autoLogoutUrl(url, autoLogout)
  }

  private def signinToken(credentials: Credentials): String = {
    val sessionJsonValue =
      s"""{"sessionId":"${credentials.accessKeyId}","sessionKey":"${credentials.secretAccessKey}","sessionToken":"${credentials.sessionToken}"}\n"""
    val session = URLEncoder.encode(sessionJsonValue, UTF_8)
    val url =
      s"$signInUrl?Action=getSigninToken&SessionType=json&Session=$session"
    val source = Source.fromURL(new URI(url).toURL)
    val response = source.getLines().mkString("\n")
    source.close()
    (Json.parse(response) \ "SigninToken").as[String]
  }

  /** Janus supports logging users out before redirecting them to the Console.
    *
    * If this setting is enabled we send the user to the console logout page,
    * with their login URL as the post-logout redirect URL. This means AWS logs
    * the user out of the console before sending them to log in with their
    * temporary session.
    *
    * NOTE: us-east-1 is required in these URLs, as per
    * https://serverfault.com/questions/985255/1097528#comment1469112_1097528
    */
  private[aws] def autoLogoutUrl(
      loginUrl: String,
      autoLogout: Boolean
  ): String = {
    if (autoLogout) {
      s"https://us-east-1.signin.aws.amazon.com/oauth?Action=logout&redirect_uri=${URLEncoder.encode(
          loginUrl.replace(
            "https://signin.aws.amazon.com/",
            "https://us-east-1.signin.aws.amazon.com/"
          ),
          "UTF-8"
        )}"
    } else {
      loginUrl
    }
  }

  /** Revokes all privileges for this account. This works by adding a condition
    * to Janus' user that denies access to any requests signed using temporary
    * credentials that were obtained after the given time.
    *
    * For more information refer to the following AWS documentation:
    * http://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_revoke-sessions.html
    */
  def disableFederation(
      account: AwsAccount,
      after: DateTime,
      roleArn: String,
      stsClient: StsClient
  ): Unit = {
    val revocationPolicyDocument = denyOlderSessionsPolicyDocument(after)

    // assume role in the target account to authenticate the revocation
    val creds = Federation.assumeRole(
      "janus",
      roleArn,
      Policies.revokeAccessPermission(account),
      stsClient,
      Federation.awsMinimumSessionLength
    )
    val sessionCredentials = AwsSessionCredentials.create(
      creds.accessKeyId(),
      creds.secretAccessKey(),
      creds.sessionToken()
    )
    val iamClient = IAM(
      sessionCredentials.accessKeyId(),
      sessionCredentials.secretAccessKey()
    )

    // remove access from assumed role
    val roleName = getRoleName(roleArn)
    val getRoleRequest = new GetRoleRequest().withRoleName(roleName)
    val role = Role(iamClient.getRole(getRoleRequest).getRole)
    val roleRevocationPolicy =
      RolePolicy(role, "janus-role-revocation-policy", revocationPolicyDocument)
    // ^
    // this name should match policy in cloudformation/federation.template.yaml
    iamClient.putRolePolicy(roleRevocationPolicy)
    iamClient.shutdown()
  }

  private[aws] def getRoleName(roleArn: String): String = {
    roleArn.split("/").last
  }

  /*
   * Denies access for requests signed with temporary credentials issued after the given time.
   *
   * Deny always beats Allow, so this will override all the user's permissions and deny everything.
   */
  private def denyOlderSessionsPolicyDocument(after: DateTime): String = {
    val revocationTime = Date.isoDateString(after)
    s"""{
        |  "Version": "2012-10-17",
        |  "Statement": [
        |    {
        |      "Effect": "Deny",
        |      "Action": [ "*" ],
        |      "Resource": [ "*" ],
        |      "Condition": {
        |        "DateLessThan": {
        |          "aws:TokenIssueTime": "$revocationTime"
        |        }
        |      }
        |    }
        |  ]
        |}""".stripMargin
  }

  implicit class IntWithDurations(val int: Int) extends AnyVal {

    /** Number of seconds in this many hours.
      */
    def hours: Duration = new Period(int, 0, 0, 0).toStandardDuration
    def hour: Duration = hours
    def minutes: Duration = new Period(0, int, 0, 0).toStandardDuration
    def minute: Duration = minutes
    def seconds: Duration = new Period(0, 0, int, 0).toStandardDuration
    def second: Duration = seconds
  }
}
