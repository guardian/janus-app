package filters

import com.gu.googleauth.{AuthAction, UserIdentity}
import models.PasskeyMode
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.mvc.{AnyContentAsFormUrlEncoded, Results}
import play.api.test.{FakeHeaders, FakeRequest}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.{
  QueryRequest,
  QueryResponse
}

import scala.concurrent.ExecutionContext.Implicits.global

class PasskeyAuthFilterTest
    extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with Results {

  /** Base stub used for modes that perform no passkey status lookup. The
    * default interface implementations will throw if any DynamoDB method is
    * called.
    */
  private given DynamoDbClient = new DynamoDbClient {
    override def serviceName(): String = "dynamodb-mock"
    override def close(): Unit = {}
  }

  /** Produces a client whose query method returns the given passkey count. */
  private def dynamoWithPasskeyCount(count: Int): DynamoDbClient =
    new DynamoDbClient {
      override def serviceName(): String = "dynamodb-mock"
      override def close(): Unit = {}
      override def query(request: QueryRequest): QueryResponse =
        QueryResponse.builder().count(count).build()
    }

  private val testHost = "test.example.com"

  private val testUser = UserIdentity(
    sub = "test-sub-id",
    email = "test@example.com",
    firstName = "Test",
    lastName = "User",
    exp = Long.MaxValue,
    avatarUrl = None
  )

  private def createRequest[A](body: A) =
    new AuthAction.UserIdentityRequest(
      testUser,
      FakeRequest("POST", "/test")
        .withHeaders(FakeHeaders())
        .withBody(body)
    )

  private val validFormBody = AnyContentAsFormUrlEncoded(
    Map(
      "credentials" -> Seq(
        """{"id":"ABCDEFGHIJKLMNOPQRSTUVWXYZ","rawId":"ABCDEFGHIJKLMNOPQRSTUVWXYZ","response":{"authenticatorData":"data","clientDataJSON":"json","signature":"sig"}}"""
      )
    )
  )

  "PasskeyAuthFilter" - {

    "when mode is Disabled" - {
      "bypasses authentication regardless of registered passkeys" in {
        val filter = new PasskeyAuthFilter(
          host = testHost,
          passkeyMode = PasskeyMode.Disabled
        )
        val result = filter.filter(createRequest(validFormBody)).futureValue
        result shouldBe None
      }
    }

    "when mode is IfUserHasPasskey" - {
      "bypasses authentication when user has no passkeys registered" in {
        given DynamoDbClient = dynamoWithPasskeyCount(0)
        val filter = new PasskeyAuthFilter(
          host = testHost,
          passkeyMode = PasskeyMode.IfUserHasPasskey
        )
        val result = filter.filter(createRequest(validFormBody)).futureValue
        result shouldBe None
      }

      "attempts passkey authentication when user has a passkey registered" in {
        given DynamoDbClient = dynamoWithPasskeyCount(1)
        val filter = new PasskeyAuthFilter(
          host = testHost,
          passkeyMode = PasskeyMode.IfUserHasPasskey
        )
        val result = filter.filter(createRequest(validFormBody)).futureValue
        result shouldBe defined
      }
    }

    "when mode is Required" - {
      "attempts passkey authentication regardless of registered passkeys" in {
        val filter = new PasskeyAuthFilter(
          host = testHost,
          passkeyMode = PasskeyMode.Required
        )
        val result = filter.filter(createRequest(validFormBody)).futureValue
        result shouldBe defined
      }
    }
  }
}
