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
    val (
      (
        stage,
        permissionId,
        accessType,
        accessSource
      ),
      request
    ) = {
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
    }.sample.get

    "should generate correct namespace" in {
      request.namespace() shouldBe s"/$stage/security/janus"
    }

    "should generate correct metric" - {
      "should generate correct metric size" in {
        request.metricData().size() shouldBe 1
      }
      val metric = request.metricData().get(0)
      "should generate correct metric name" in {
        metric.metricName() shouldBe failedRequestMetricName
      }
      "should generate correct metric value" in {
        metric.value() shouldBe 1
      }
      "should generate correct metric unit" in {
        metric.unit() shouldBe StandardUnit.COUNT
      }

      "should generate correct metric dimensions" - {
        val dimensions = metric.dimensions().asScala
        val dimensionNames = dimensions.map(_.name)
        s"should generate correct ${permissionIdDimensionName} dimension" in {
          dimensionNames should contain(permissionIdDimensionName)
          dimensions
            .filter(_.name == permissionIdDimensionName)
            .map(_.value()) should contain(
            permissionId
          )
        }
        s"should generate correct ${accessTypeDimensionName} dimension" in {
          dimensionNames should contain(accessTypeDimensionName)
          dimensions
            .find(_.name == accessTypeDimensionName)
            .flatMap(dimension =>
              JanusAccessType.fromString(dimension.value())
            ) should contain(
            accessType
          )
        }
        s"should generate correct ${accessSourceDimensionName} dimension" in {
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

  "getDeniedMetricRequest" - {
    val (
      (
        stage,
        permissionId,
        accessType
      ),
      request
    ) = {
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
    }.sample.get

    "should generate correct namespace" in {
      request.namespace() shouldBe s"/$stage/security/janus"
    }
    "should generate correct metric" - {
      "should generate correct metric size" in {
        request.metricData().size() shouldBe 1
      }
      val metric = request.metricData().get(0)
      "should generate correct metric name" in {
        metric.metricName() shouldBe deniedRequestMetricName
      }
      "should generate correct metric value" in {
        metric.value() shouldBe 1
      }
      "should generate correct metric unit" in {
        metric.unit() shouldBe StandardUnit.COUNT
      }
      "should generate correct metric dimensions" - {
        val dimensions = metric.dimensions().asScala
        val dimensionNames = dimensions.map(_.name)
        s"should generate correct ${permissionIdDimensionName} dimension" in {
          dimensionNames should contain(permissionIdDimensionName)
          dimensions
            .filter(_.name == permissionIdDimensionName)
            .map(_.value()) should contain(
            permissionId
          )
        }
        s"should generate correct ${accessTypeDimensionName} dimension" in {
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
  }

  "getSuccessfulMetricRequest" - {
    val (
      (
        stage,
        permissionId,
        accessType,
        accessSource,
        size
      ),
      request
    ) = {
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

    }.sample.get

    "should generate correct namespace" in {
      request.namespace() shouldBe s"/$stage/security/janus"
    }
    "should generate correct metric" - {
      "should generate correct metric size" in {
        request.metricData().size() shouldBe 1
      }
      val metric = request.metricData().get(0)
      "should generate correct metric name" in {
        metric.metricName() shouldBe successfulRequestMetricName
      }
      "should generate correct metric value" in {
        metric.value() shouldBe size
      }
      "should generate correct metric unit" in {
        metric.unit() shouldBe StandardUnit.BYTES
      }
      "should generate correct metric dimensions" - {
        val dimensions = metric.dimensions().asScala
        val dimensionNames = dimensions.map(_.name)
        s"should generate correct ${permissionIdDimensionName} dimension" in {
          dimensionNames should contain(permissionIdDimensionName)
          dimensions
            .filter(_.name == permissionIdDimensionName)
            .map(_.value()) should contain(
            permissionId
          )
        }
        s"should generate correct ${accessTypeDimensionName} dimension" in {
          dimensionNames should contain(accessTypeDimensionName)
          dimensions
            .find(_.name == accessTypeDimensionName)
            .flatMap(dimension =>
              JanusAccessType.fromString(dimension.value())
            ) should contain(
            accessType
          )
        }
        s"should generate correct ${accessSourceDimensionName} dimension" in {
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

  "getTooLargeMetricRequest" - {
    val (
      (
        stage,
        permissionId,
        accessType,
        accessSource,
        size
      ),
      request
    ) = {

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

    }.sample.get

    "should generate correct namespace" in {
      request.namespace() shouldBe s"/$stage/security/janus"
    }
    "should generate correct metric" - {
      "should generate correct metric size" in {
        request.metricData().size() shouldBe 1
      }
      val metric = request.metricData().get(0)
      "should generate correct metric name" in {
        metric.metricName() shouldBe tooLargeRequestMetricName
      }
      "should generate correct metric value" in {
        metric.value() shouldBe size
      }
      "should generate correct metric unit" in {
        metric.unit() shouldBe StandardUnit.PERCENT
      }
      "should generate correct metric dimensions" - {
        val dimensions = metric.dimensions().asScala
        val dimensionNames = dimensions.map(_.name)
        s"should generate correct ${permissionIdDimensionName} dimension" in {
          dimensionNames should contain(permissionIdDimensionName)
          dimensions
            .filter(_.name == permissionIdDimensionName)
            .map(_.value()) should contain(
            permissionId
          )
        }
        s"should generate correct ${accessTypeDimensionName} dimension" in {
          dimensionNames should contain(accessTypeDimensionName)
          dimensions
            .find(_.name == accessTypeDimensionName)
            .flatMap(dimension =>
              JanusAccessType.fromString(dimension.value())
            ) should contain(
            accessType
          )
        }
        s"should generate correct ${accessSourceDimensionName} dimension" in {
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
}
