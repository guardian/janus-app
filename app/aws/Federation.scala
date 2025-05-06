package aws

import com.gu.janus.model.{AwsAccount, Permission}
import data.Policies
import logic.Date
import play.api.Mode
import play.api.Mode.Prod
import play.api.libs.json.Json
import software.amazon.awssdk.auth.credentials._
import software.amazon.awssdk.regions.Region.EU_WEST_1
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.PutRolePolicyRequest
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model._

import java.net.{URI, URLEncoder}
import java.nio.charset.StandardCharsets.UTF_8
import java.time.{Clock, Duration, Instant, ZonedDateTime}
import scala.io.Source
import scala.jdk.CollectionConverters.SeqHasAsJava

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
    *
    * @param permission
    *   Used to determine if the session duration should be short-term or
    *   standard.
    * @param requestedDuration
    *   Optionally, a user can request a duration for their session, which may
    *   or may not be honoured.
    * @param optClock
    *   [[Clock]] to use to determine the current time.
    */
  def duration(
      permission: Permission,
      requestedDuration: Option[Duration] = None,
      optClock: Option[Clock] = None
  ): Duration = {
    if (permission.shortTerm) {
      // short term permission, give them requested or default (limited by max)
      val calculated = requestedDuration.fold(defaultShortTime) { requested =>
        Date.minDuration(requested, maxShortTime)
      }
      Date.maxDuration(calculated, minShortTime)
    } else {
      // use requested or default (limited by max)
      // if nothing is requested, try to give them until 19:00 local time (requires timezone)
      val calculated = requestedDuration match {
        case None =>
          optClock.fold(defaultLongTime) { clock =>
            val now = ZonedDateTime.now(clock)
            val localEndOfWork = {
              val withTime =
                now.withHour(19).withMinute(0).withSecond(0).withNano(0)
              if (withTime.isBefore(now)) withTime.plusDays(1)
              else withTime
            }
            val durationToEndOfWork = Duration.between(now, localEndOfWork)
            if (durationToEndOfWork.compareTo(maxLongTime) < 0)
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
    val requestBuilder = AssumeRoleRequest
      .builder()
      .roleArn(roleArn)
      .roleSessionName(username)
      .durationSeconds(duration.getSeconds.toInt)
    permission.policy.foreach { policy =>
      requestBuilder.policy(policy)
    }
    permission.managedPolicyArns.foreach { managedPolicyArns =>
      requestBuilder.policyArns(
        managedPolicyArns.map { arn =>
          PolicyDescriptorType.builder().arn(arn).build()
        }.asJava
      )
    }
    val request = requestBuilder.build()
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
  )(implicit mode: Mode): Unit = {
    val revocationPolicyDocument = denyOlderSessionsPolicyDocument(after)

    val username = mode match {
      case Prod => "janus"
      case _    => "janus-dev"
    }

    // assume role in the target account to authenticate the revocation
    val creds = Federation.assumeRole(
      username,
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
    val iamClient = IamClient.builder().region(EU_WEST_1).credentialsProvider(provider).build()

    // remove access from assumed role
    val roleName = getRoleName(roleArn)
    val roleRevocationPolicy = PutRolePolicyRequest
      .builder()
      .roleName(roleName)
      .policyName("janus-role-revocation-policy")
      //           ^
      // this name should match policy in cloudformation/federation.template.yaml
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
