package conf

import com.google.auth.oauth2.ServiceAccountCredentials
import com.gu.googleauth.{
  AntiForgeryChecker,
  GoogleAuthConfig,
  GoogleGroupChecker
}
import com.gu.janus.JanusConfig
import com.gu.janus.model._
import com.gu.play.secretrotation.SnapshotProvider
import models._
import play.api.Configuration

import java.io.{File, FileInputStream}
import scala.util.Try

object Config {
  def roleArn(awsAccountAuthConfigKey: String, config: Configuration): String =
    requiredString(config, s"federation.$awsAccountAuthConfigKey.aws.roleArn")

  def validateAccountConfig(
      janusData: JanusData,
      config: Configuration
  ): AccountConfigStatus = {
    val janusAccountKeys = janusData.accounts.map(_.authConfigKey)
    Try(config.get[Configuration]("federation")).fold(
      { err =>
        if (janusAccountKeys.isEmpty) ConfigSuccess
        else FederationConfigError(err)
      },
      { federationConfig =>
        val configAccountKeys = federationConfig.subKeys

        if (configAccountKeys == janusAccountKeys) {
          ConfigSuccess
        } else if (configAccountKeys.subsetOf(janusAccountKeys)) {
          // config is missing for one or more janusData accounts
          ConfigError(janusAccountKeys -- configAccountKeys)
        } else {
          // config contains unnecessary entries for accounts that are not in the janusData
          ConfigWarn(configAccountKeys -- janusAccountKeys)
        }
      }
    )
  }

  def host(config: Configuration): String = {
    requiredString(config, "host")
  }
  def janusData(config: Configuration): JanusData = {
    config
      .getOptional[String]("data.fileLocation")
      .map(filePath => JanusConfig.load(new File(filePath)))
      .getOrElse(JanusConfig.load("janusData.conf"))
  }

  def googleSettings(
      config: Configuration,
      secretStateSupplier: SnapshotProvider
  ): GoogleAuthConfig = {
    val clientId = requiredString(config, "auth.google.clientId")
    val clientSecret = requiredString(config, "auth.google.clientSecret")
    val domain = requiredString(config, "auth.domain")
    val redirectUrl = s"${requiredString(config, "host")}/oauthCallback"

    GoogleAuthConfig(
      clientId = clientId,
      clientSecret = clientSecret,
      redirectUrl = redirectUrl,
      domains = List(domain),
      antiForgeryChecker = AntiForgeryChecker(secretStateSupplier)
    )
  }

  def googleGroupChecker(config: Configuration): GoogleGroupChecker = {
    val twoFAUser = requiredString(config, "auth.google.2faUser")
    val serviceAccountCertPath =
      requiredString(config, "auth.google.serviceAccountCertPath")

    val credentials: ServiceAccountCredentials = {
      val jsonCertStream =
        Try(new FileInputStream(serviceAccountCertPath))
          .getOrElse(
            throw new RuntimeException(
              s"Could not load service account JSON from $serviceAccountCertPath"
            )
          )
      ServiceAccountCredentials.fromStream(jsonCertStream)
    }

    new GoogleGroupChecker(twoFAUser, credentials)
  }

  def twoFAGroup(config: Configuration): String = {
    requiredString(config, "auth.google.2faGroupId")
  }

  private def requiredString(config: Configuration, key: String): String = {
    config.getOptional[String](key).getOrElse {
      throw new RuntimeException(s"Missing required config property $key")
    }
  }
}
