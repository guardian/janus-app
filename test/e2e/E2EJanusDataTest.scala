package e2e

import com.gu.googleauth.DevAuthBypass
import com.gu.janus.model.Permission
import com.typesafe.config.ConfigFactory
import conf.Config
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import play.api.libs.json.Json

/** Ensure that all test data is safe.
  */
class E2EJanusDataTest extends AnyFreeSpec with Matchers {
  private val config = Configuration(ConfigFactory.load("e2e"))
  private val username =
    logic.UserAccess.username(DevAuthBypass.defaultIdentity)
  private val dummyRoles = Map(
    "test-aws-account" -> "arn:aws:iam::000000000001:role/e2e-deny-all",
    "test-aws-account-2" -> "arn:aws:iam::000000000002:role/e2e-deny-all",
    "test-aws-account-3" -> "arn:aws:iam::000000000003:role/e2e-deny-all"
  )
  private val denyAll = Json.parse(
    """{"Version":"2012-10-17","Statement":[{"Effect":"Deny","Action":["*"],"Resource":["*"]}]}"""
  )

  E2EJanusData.fixtures.toSeq.sortBy(_._1).foreach { case (name, data) =>
    s"$name grants only deny-all access to the bypass user using dummy roles" in {
      data.access.defaultPermissions shouldBe empty
      data.superuser.userAccess shouldBe empty
      data.superuser.defaultPermissions shouldBe empty
      data.permissionsRepo shouldBe None
      data.access.userAccess.keySet -- Set(username) shouldBe empty
      data.access.userAccess.values.foreach { entry =>
        entry.policyGrants shouldBe empty
      }
      data.support.rota.values.map(_._1).toSet -- Set(username) shouldBe empty

      val permissions = Permission.allPermissions(data)
      permissions should not be empty
      permissions.map(_.account) shouldEqual data.accounts
      permissions.foreach { permission =>
        withClue(s"${permission.id}: ") {
          permission.label shouldEqual "e2e-deny-all"
          permission.shortTerm shouldBe true
          permission.managedPolicyArns shouldBe None
          permission.policy.map(Json.parse) shouldEqual Some(denyAll)
        }
      }
      data.accounts.foreach { account =>
        withClue(s"${account.authConfigKey}: ") {
          dummyRoles should contain key account.authConfigKey
          Config.roleArn(account.authConfigKey, config) shouldEqual
            dummyRoles(account.authConfigKey)
        }
      }
    }
  }
}
