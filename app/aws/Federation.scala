package aws

import com.gu.janus.model.{AwsAccount, Permission}
import data.Policies
import logic.Date
import play.api.libs.json.Json
import software.amazon.awssdk.auth.credentials._
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.PutRolePolicyRequest
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model._

import java.net.{URI, URLEncoder}
import java.nio.charset.StandardCharsets.UTF_8
import java.time.{Duration, Instant, ZoneId, ZonedDateTime}
import scala.io.Source
import scala.math.Ordering.Implicits.infixOrderingOps

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
      requestedSeconds: Option[Duration],
      timezone: Option[ZoneId]
  ): Duration = {
    if (permission.shortTerm) {
      // short term
      val calculated =
        requestedSeconds.getOrElse(defaultShortTime).min(maxShortTime)
      calculated.max(minShortTime)
    } else {
      // long term
      val calculated = requestedSeconds.getOrElse {
        timezone.fold(defaultLongTime) { tz =>
          val now = ZonedDateTime.now(tz)
          val withTime =
            now.withHour(19).withMinute(0).withSecond(0).withNano(0)
          val endOfWork =
            if (withTime.isBefore(now)) withTime.plusDays(1) else withTime
          val durToEndOfWork = Duration.between(now, endOfWork)
          if (durToEndOfWork.compareTo(maxLongTime) < 0) durToEndOfWork
          else defaultLongTime
        }
      }
      calculated.min(maxLongTime).max(minLongTime)
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
      .durationSeconds(duration.getSeconds.toInt)
      .build()
    val response = sts.assumeRole(request)
    response.credentials()
  }

  def generateLoginUrl(
      temporaryCredentials: Credentials,
      host: String
  ): String = {
    // See https://github.com/seratch/AWScala/blob/5d9012dec25eafc4275765bfc5cbe46c3ed37ba2/sts/src/main/scala/awscala/sts/STS.scala
    val token = URLEncoder.encode(signinToken(temporaryCredentials), UTF_8)
    val issuer = URLEncoder.encode(host, UTF_8)
    val destination = URLEncoder.encode(consoleUrl, UTF_8)
    s"$signInUrl?Action=login&SigninToken=$token&Issuer=$issuer&Destination=$destination"
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

  /** Revokes all privileges for this account. This works by adding a condition
    * to Janus' user that denies access to any requests signed using temporary
    * credentials that were obtained after the given time.
    *
    * For more information refer to the following AWS documentation:
    * http://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_revoke-sessions.html
    */
  def disableFederation(
      account: AwsAccount,
      after: Instant,
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
      creds.accessKeyId,
      creds.secretAccessKey,
      creds.sessionToken
    )
    val provider = StaticCredentialsProvider.create(sessionCredentials)
    val iamClient = IamClient.builder().credentialsProvider(provider).build()

    // remove access from assumed role
    val roleName = getRoleName(roleArn)
    val roleRevocationPolicy = PutRolePolicyRequest
      .builder()
      .roleName(roleName)
      .policyName("janus-role-revocation-policy")
      .policyDocument(revocationPolicyDocument)
      .build()
    iamClient.putRolePolicy(roleRevocationPolicy)
    iamClient.close()
  }

  private[aws] def getRoleName(roleArn: String): String = {
    roleArn.split("/").last
  }

  /*
   * Denies access for requests signed with temporary credentials issued after the given time.
   *
   * Deny always beats Allow, so this will override all the user's permissions and deny everything.
   */
  private def denyOlderSessionsPolicyDocument(after: Instant): String = {
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
    def hours: Duration = Duration.ofHours(int)
    def hour: Duration = hours
    def minutes: Duration = Duration.ofMinutes(int)
    def minute: Duration = minutes
    def seconds: Duration = Duration.ofSeconds(int)
    def second: Duration = seconds
  }
}
