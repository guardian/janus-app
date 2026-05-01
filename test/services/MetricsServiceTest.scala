package services

import com.gu.janus.model.{JConsole, JCredentials, JanusAccessType}
import models.AccessSource
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import services.MetricsService.*
import software.amazon.awssdk.services.cloudwatch.model.{
  PutMetricDataRequest,
  StandardUnit
}
import software.amazon.awssdk.services.sts.model.PackedPolicyTooLargeException

import scala.jdk.CollectionConverters.*

class MetricsServiceTest
    extends AnyFreeSpec
    with Matchers
    with ScalaCheckPropertyChecks {

  private def accessSourceGen =
    Gen.oneOf(AccessSource.Internal, AccessSource.Admin, AccessSource.Support)
  private def accessTypeGen = Gen.oneOf(JCredentials, JConsole)
  private def permissionIdGen = Gen.alphaNumStr.suchThat(_.nonEmpty)
  private def stageGen = Gen.oneOf("DEV", "CODE", "PROD")

  "getFailedMetricRequest" - {
    def failedMetricRequestGen: Gen[
      (
          (String, String, JanusAccessType, AccessSource),
          PutMetricDataRequest
      )
    ] =
      for {
        stage <- stageGen
        permissionId <- permissionIdGen
        accessType <- accessTypeGen
        accessSource <- accessSourceGen
      } yield (
        (stage, permissionId, accessType, accessSource),
        MetricsService(stage).getFailedMetricRequest(
          permissionId,
          accessType,
          accessSource
        )
      )

    "should generate exactly correct objects" in {
      forAll(failedMetricRequestGen) {
        case (
              (
                stage,
                permissionId,
                accessType,
                accessSource
              ),
              request
            ) =>
          request.namespace() shouldBe s"/$stage/security/janus"
          request.metricData().size() shouldBe 1
          val metric = request.metricData().get(0)
          metric.metricName() shouldBe failedRequestMetricName
          metric.value() shouldBe 1
          metric.unit() shouldBe StandardUnit.COUNT
          val dimensions = metric.dimensions().asScala
          val dimensionNames = dimensions.map(_.name)
          dimensionNames should contain(permissionIdDimensionName)
          dimensions
            .filter(_.name == permissionIdDimensionName)
            .map(_.value()) should contain(
            permissionId
          )
          dimensionNames should contain(accessTypeDimensionName)
          dimensions
            .find(_.name == accessTypeDimensionName)
            .flatMap(dimension =>
              JanusAccessType.fromString(dimension.value())
            ) should contain(
            accessType
          )
          dimensionNames should contain(accessSourceDimensionName)
          dimensions
            .find(_.name == accessSourceDimensionName)
            .map(dimension =>
              AccessSource.valueOf(dimension.value())
            ) should contain(
            accessSource
          )
      }
    }
  }

  "getDeniedMetricRequest" - {
    def deniedMetricRequestGen: Gen[
      (
          (String, String, JanusAccessType),
          PutMetricDataRequest
      )
    ] =
      for {
        stage <- stageGen
        permissionId <- permissionIdGen
        accessType <- accessTypeGen
      } yield (
        (stage, permissionId, accessType),
        MetricsService(stage).getDeniedMetricRequest(
          permissionId,
          accessType
        )
      )

    "should generate exactly correct objects" in {
      forAll(deniedMetricRequestGen) {
        case (
              (
                stage,
                permissionId,
                accessType
              ),
              request
            ) =>
          request.namespace() shouldBe s"/$stage/security/janus"
          request.metricData().size() shouldBe 1
          val metric = request.metricData().get(0)
          metric.metricName() shouldBe deniedRequestMetricName
          metric.value() shouldBe 1
          metric.unit() shouldBe StandardUnit.COUNT
          val dimensions = metric.dimensions().asScala
          val dimensionNames = dimensions.map(_.name)
          dimensionNames should contain(permissionIdDimensionName)
          dimensions
            .filter(_.name == permissionIdDimensionName)
            .map(_.value()) should contain(
            permissionId
          )
          dimensionNames should contain(accessTypeDimensionName)
          dimensions
            .find(_.name == accessTypeDimensionName)
            .flatMap(dimension =>
              JanusAccessType.fromString(dimension.value())
            ) should contain(
            accessType
          )
      }
    }
  }

  "getSuccessfulMetricRequest" - {
    def successfulMetricRequestGen: Gen[
      (
          (String, String, JanusAccessType, AccessSource, Int),
          PutMetricDataRequest
      )
    ] =
      for {
        stage <- stageGen
        permissionId <- permissionIdGen
        accessType <- accessTypeGen
        accessSource <- accessSourceGen
        size <- Gen.size
      } yield (
        (stage, permissionId, accessType, accessSource, size),
        MetricsService(stage).getSuccessfulMetricRequest(
          permissionId,
          accessType,
          accessSource,
          size
        )
      )

    "should generate exactly correct objects" in {
      forAll(successfulMetricRequestGen) {
        case (
              (
                stage,
                permissionId,
                accessType,
                accessSource,
                size
              ),
              request
            ) =>
          request.namespace() shouldBe s"/$stage/security/janus"
          request.metricData().size() shouldBe 1
          val metric = request.metricData().get(0)
          metric.metricName() shouldBe successfulRequestMetricName
          metric.value() shouldBe size
          metric.unit() shouldBe StandardUnit.BYTES
          val dimensions = metric.dimensions().asScala
          val dimensionNames = dimensions.map(_.name)
          dimensionNames should contain(permissionIdDimensionName)
          dimensions
            .filter(_.name == permissionIdDimensionName)
            .map(_.value()) should contain(
            permissionId
          )
          dimensionNames should contain(accessTypeDimensionName)
          dimensions
            .find(_.name == accessTypeDimensionName)
            .flatMap(dimension =>
              JanusAccessType.fromString(dimension.value())
            ) should contain(
            accessType
          )
          dimensionNames should contain(accessSourceDimensionName)
          dimensions
            .find(_.name == accessSourceDimensionName)
            .map(dimension =>
              AccessSource.valueOf(dimension.value())
            ) should contain(
            accessSource
          )
      }
    }
  }

  "getTooLargeMetricRequest" - {
    def tooLargeMetricRequestGen: Gen[
      (
          (String, String, JanusAccessType, AccessSource, Int),
          PutMetricDataRequest
      )
    ] =
      for {
        stage <- stageGen
        permissionId <- permissionIdGen
        accessType <- accessTypeGen
        accessSource <- accessSourceGen
        size <- Gen.size
      } yield (
        (stage, permissionId, accessType, accessSource, size),
        MetricsService(stage).getTooLargeMetricRequest(
          permissionId,
          accessType,
          accessSource,
          PackedPolicyTooLargeException
            .builder()
            .message(s"Packed policy consumes $size% of allotted space")
            .build()
        )
      )

    "should generate exactly correct objects" in {
      forAll(tooLargeMetricRequestGen) {
        case (
              (
                stage,
                permissionId,
                accessType,
                accessSource,
                size
              ),
              request
            ) =>
          request.namespace() shouldBe s"/$stage/security/janus"
          request.metricData().size() shouldBe 1
          val metric = request.metricData().get(0)
          metric.metricName() shouldBe tooLargeRequestMetricName
          metric.value() shouldBe size
          metric.unit() shouldBe StandardUnit.PERCENT
          val dimensions = metric.dimensions().asScala
          val dimensionNames = dimensions.map(_.name)
          dimensionNames should contain(permissionIdDimensionName)
          dimensions
            .filter(_.name == permissionIdDimensionName)
            .map(_.value()) should contain(
            permissionId
          )
          dimensionNames should contain(accessTypeDimensionName)
          dimensions
            .find(_.name == accessTypeDimensionName)
            .flatMap(dimension =>
              JanusAccessType.fromString(dimension.value())
            ) should contain(
            accessType
          )
          dimensionNames should contain(accessSourceDimensionName)
          dimensions
            .find(_.name == accessSourceDimensionName)
            .map(dimension =>
              AccessSource.valueOf(dimension.value())
            ) should contain(
            accessSource
          )
      }
    }
  }
}
