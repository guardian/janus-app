package aws

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.KeyType.{HASH, RANGE}
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType.S
import software.amazon.awssdk.services.dynamodb.model._

class PasskeyDBTest extends AnyFreeSpec with Matchers {

  /** Once the table create code was removed, this test class was empty. TODO:
    * Add some tests.
    */
}
