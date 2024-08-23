package aws

import awscala.Credentials
import awscala.iam._
import awscala.sts.{FederationToken, STS, TemporaryCredentials}
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.services.identitymanagement.model.GetRoleRequest
import com.amazonaws.services.securitytoken
import com.gu.janus.model.{AwsAccount, Permission}
import data.Policies
import logic.Date
import org.joda.time.{DateTime, DateTimeZone, Duration, Period}

import java.net.URLEncoder

object Federation {

  /** Credential/Console lease times. Defaults are used when user doesn't
    * request specific time periods, max will limit how long can be requested.
    */
  val maxShortTime = 3.hours
  val minShortTime = 15.minutes
  val defaultShortTime = 1.hour
  val maxLongTime = 10.hours
  val minLongTime = 1.hour
  val defaultLongTime = 8.hours

  val awsMinimumSessionLength = 900.seconds

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

  val getStsClient: ((String, String)) => STS = {
    case (awsKeyId, awsSecretKey) =>
      STS(awsKeyId, awsSecretKey)
  }

  def assumeRole(
      username: String,
      roleArn: String,
      permission: Permission,
      sts: STS,
      duration: Duration
  ): TemporaryCredentials = {
    val assumeRoleReq = new securitytoken.model.AssumeRoleRequest()
      .withRoleArn(roleArn)
      .withRoleSessionName(username)
      .withPolicy(permission.policy)
      .withDurationSeconds(duration.getStandardSeconds.toInt)
    val response = sts.assumeRole(assumeRoleReq)
    TemporaryCredentials(response.getCredentials)
  }

  def generateLoginUrl(
      temporaryCredentials: TemporaryCredentials,
      host: String,
      autoLogout: Boolean,
      sts: STS
  ): String = {
    val url = sts.loginUrl(
      credentials = temporaryCredentials,
      consoleUrl = "https://console.aws.amazon.com/",
      issuerUrl = host
    )
    autoLogoutUrl(url, autoLogout)
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

  def credentials(federationToken: FederationToken): TemporaryCredentials = {
    federationToken.credentials
  }

  /** Revokes all privileges for this account. This works by adding a condition
    * to Janus' user that denys access to any requests signed using temporary
    * credentials that were obtained after the given time.
    *
    * For more information refer to the following AWS documentation:
    * http://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_revoke-sessions.html
    */
  def disableFederation(
      account: AwsAccount,
      after: DateTime,
      roleArn: String,
      stsClient: STS
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
    val sessionCredentials =
      Credentials(creds.accessKeyId, creds.secretAccessKey, creds.sessionToken)
    val provider = new AWSStaticCredentialsProvider(sessionCredentials)
    val iamClient = IAM(provider)

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
  def denyOlderSessionsPolicyDocument(after: DateTime): String = {
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
    def hour = hours
    def minutes: Duration = new Period(0, int, 0, 0).toStandardDuration
    def minute = minutes
    def seconds: Duration = new Period(0, 0, int, 0).toStandardDuration
    def second = seconds
  }
}
