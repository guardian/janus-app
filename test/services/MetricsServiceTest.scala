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

  private def permissionIdGen = Gen.listOf(Gen.alphaNumChar).map(_.mkString)

  private def stageGen = Gen.oneOf("DEV", "CODE", "PROD")

  "getFailedMetricRequest" - {
    case class TestWorld(
        stage: String,
        permissionId: String,
        accessType: JanusAccessType,
        accessSource: AccessSource
    )

    val getFailedMetricRequestGen: Gen[(TestWorld, PutMetricDataRequest)] =
      for {
        stage <- stageGen
        permissionId <- permissionIdGen
        accessType <- accessTypeGen
        accessSource <- accessSourceGen
      } yield (
        TestWorld(stage, permissionId, accessType, accessSource),
        MetricsService(stage).getFailedMetricRequest(
          permissionId,
          accessType,
          accessSource
        )
      )

    "should generate correct namespace" in {
      forAll(getFailedMetricRequestGen) {
        case (TestWorld(stage, _, _, _), request) =>
          request
            .namespace() shouldBe s"/$stage/${MetricsService.stack}/${MetricsService.app}"
      }
    }

    "should generate correct metric" - {
      "should generate correct metric size" in {
        forAll(getFailedMetricRequestGen) { case (_, request) =>
          request.metricData().size() shouldBe 1
        }
      }
      "should generate correct metric name" in {
        forAll(getFailedMetricRequestGen) { case (_, request) =>
          request
            .metricData()
            .get(0)
            .metricName() shouldBe failedRequestMetricName
        }
      }
      "should generate correct metric value" in {
        forAll(getFailedMetricRequestGen) { case (_, request) =>
          request.metricData().get(0).value() shouldBe 1
        }
      }
      "should generate correct metric unit" in {
        forAll(getFailedMetricRequestGen) { case (_, request) =>
          request.metricData().get(0).unit() shouldBe StandardUnit.COUNT
        }
      }

      "should generate correct metric dimensions" - {
        s"should generate correct $permissionIdDimensionName dimension" in {

          forAll(getFailedMetricRequestGen) {
            case (TestWorld(_, permissionId, _, _), request) =>
              val dimensions = request.metricData().get(0).dimensions().asScala
              val dimensionNames = dimensions.map(_.name)
              dimensionNames should contain(permissionIdDimensionName)
              dimensions
                .filter(_.name == permissionIdDimensionName)
                .map(_.value()) should contain(
                permissionId
              )
          }
        }
        s"should generate correct $accessTypeDimensionName dimension" in {
          forAll(getFailedMetricRequestGen) {
            case (TestWorld(_, _, accessType, _), request) =>
              val dimensions = request.metricData().get(0).dimensions().asScala
              val dimensionNames = dimensions.map(_.name)
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
        s"should generate correct $accessSourceDimensionName dimension" in {
          forAll(getFailedMetricRequestGen) {
            case (TestWorld(_, _, _, accessSource), request) =>
              val dimensions = request.metricData().get(0).dimensions().asScala
              val dimensionNames = dimensions.map(_.name)
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

  "getDeniedMetricRequest" - {
    case class TestWorld(
        stage: String,
        permissionId: String,
        accessType: JanusAccessType
    )
    val getDeniedMetricRequestGen: Gen[(TestWorld, PutMetricDataRequest)] =
      for {
        stage <- stageGen
        permissionId <- permissionIdGen
        accessType <- accessTypeGen
      } yield (
        TestWorld(stage, permissionId, accessType),
        MetricsService(stage).getDeniedMetricRequest(
          permissionId,
          accessType
        )
      )

    "should generate correct namespace" in {
      forAll(getDeniedMetricRequestGen) {
        case (TestWorld(stage, _, _), request) =>
          request
            .namespace() shouldBe s"/$stage/${MetricsService.stack}/${MetricsService.app}"
      }
    }
    "should generate correct metric" - {
      "should generate correct metric size" in {
        forAll(getDeniedMetricRequestGen) { case (_, request) =>
          request.metricData().size() shouldBe 1
        }
      }
      "should generate correct metric name" in {
        forAll(getDeniedMetricRequestGen) { case (_, request) =>
          request
            .metricData()
            .get(0)
            .metricName() shouldBe deniedRequestMetricName
        }
      }
      "should generate correct metric value" in {
        forAll(getDeniedMetricRequestGen) { case (_, request) =>
          request.metricData().get(0).value() shouldBe 1
        }
      }
      "should generate correct metric unit" in {
        forAll(getDeniedMetricRequestGen) { case (_, request) =>
          request.metricData().get(0).unit() shouldBe StandardUnit.COUNT
        }
      }
      "should generate correct metric dimensions" - {
        s"should generate correct $permissionIdDimensionName dimension" in {
          forAll(getDeniedMetricRequestGen) {
            case (TestWorld(_, permissionId, _), request) =>
              val dimensions = request.metricData().get(0).dimensions().asScala
              val dimensionNames = dimensions.map(_.name)
              dimensionNames should contain(permissionIdDimensionName)
              dimensions
                .filter(_.name == permissionIdDimensionName)
                .map(_.value()) should contain(
                permissionId
              )
          }
        }
        s"should generate correct $accessTypeDimensionName dimension" in {
          forAll(getDeniedMetricRequestGen) {
            case (TestWorld(_, _, accessType), request) =>
              val dimensions = request.metricData().get(0).dimensions().asScala
              val dimensionNames = dimensions.map(_.name)
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
  }

  "getSuccessfulMetricRequest" - {
    case class TestWorld(
        stage: String,
        permissionId: String,
        accessType: JanusAccessType,
        accessSource: AccessSource,
        size: Int
    )
    val getSuccessfulMetricRequestGen: Gen[(TestWorld, PutMetricDataRequest)] =
      for {
        stage <- stageGen
        permissionId <- permissionIdGen
        accessType <- accessTypeGen
        accessSource <- accessSourceGen
        size <- Gen.choose(90, 110)
      } yield (
        TestWorld(stage, permissionId, accessType, accessSource, size),
        MetricsService(stage).getSuccessfulMetricRequest(
          permissionId,
          accessType,
          accessSource,
          size
        )
      )

    "should generate correct namespace" in {
      forAll(getSuccessfulMetricRequestGen) {
        case (TestWorld(stage, _, _, _, _), request) =>
          request
            .namespace() shouldBe s"/$stage/${MetricsService.stack}/${MetricsService.app}"
      }
    }
    "should generate correct metric" - {
      "should generate correct metric size" in {
        forAll(getSuccessfulMetricRequestGen) { case (_, request) =>
          request.metricData().size() shouldBe 1
        }
      }
      "should generate correct metric name" in {
        forAll(getSuccessfulMetricRequestGen) { case (_, request) =>
          request
            .metricData()
            .get(0)
            .metricName() shouldBe successfulRequestMetricName
        }
      }
      "should generate correct metric value" in {
        forAll(getSuccessfulMetricRequestGen) {
          case (TestWorld(_, _, _, _, size), request) =>
            request.metricData().get(0).value() shouldBe size
        }
      }
      "should generate correct metric unit" in {
        forAll(getSuccessfulMetricRequestGen) { case (_, request) =>
          request.metricData().get(0).unit() shouldBe StandardUnit.BYTES
        }
      }
      "should generate correct metric dimensions" - {
        s"should generate correct $permissionIdDimensionName dimension" in {
          forAll(getSuccessfulMetricRequestGen) {
            case (TestWorld(_, permissionId, _, _, _), request) =>
              val dimensions = request.metricData().get(0).dimensions().asScala
              val dimensionNames = dimensions.map(_.name)
              dimensionNames should contain(permissionIdDimensionName)
              dimensions
                .filter(_.name == permissionIdDimensionName)
                .map(_.value()) should contain(
                permissionId
              )
          }
        }
        s"should generate correct $accessTypeDimensionName dimension" in {
          forAll(getSuccessfulMetricRequestGen) {
            case (TestWorld(_, _, accessType, _, _), request) =>
              val dimensions = request.metricData().get(0).dimensions().asScala
              val dimensionNames = dimensions.map(_.name)
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
        s"should generate correct $accessSourceDimensionName dimension" in {
          forAll(getSuccessfulMetricRequestGen) {
            case (TestWorld(_, _, _, accessSource, _), request) =>
              val dimensions = request.metricData().get(0).dimensions().asScala
              val dimensionNames = dimensions.map(_.name)
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

  "getTooLargeMetricRequest" - {
    case class TestWorld(
        stage: String,
        permissionId: String,
        accessType: JanusAccessType,
        accessSource: AccessSource,
        size: Int
    )
    val getTooLargeMetricRequestGen: Gen[(TestWorld, PutMetricDataRequest)] =
      for {
        stage <- stageGen
        permissionId <- permissionIdGen
        accessType <- accessTypeGen
        accessSource <- accessSourceGen
        size <- Gen.choose(90, 110)
      } yield (
        TestWorld(stage, permissionId, accessType, accessSource, size),
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

    "should generate correct namespace" in {
      forAll(getTooLargeMetricRequestGen) {
        case (TestWorld(stage, _, _, _, _), request) =>
          request
            .namespace() shouldBe s"/$stage/${MetricsService.stack}/${MetricsService.app}"
      }
    }
    "should generate correct metric" - {
      "should generate correct metric size" in {
        forAll(getTooLargeMetricRequestGen) { case (_, request) =>
          request.metricData().size() shouldBe 1
        }
      }
      "should generate correct metric name" in {
        forAll(getTooLargeMetricRequestGen) { case (_, request) =>
          request
            .metricData()
            .get(0)
            .metricName() shouldBe tooLargeRequestMetricName
        }
      }
      "should generate correct metric value" in {
        forAll(getTooLargeMetricRequestGen) {
          case (TestWorld(_, _, _, _, size), request) =>
            request.metricData().get(0).value() shouldBe size
        }
      }
      "should generate correct metric unit" in {
        forAll(getTooLargeMetricRequestGen) { case (_, request) =>
          request.metricData().get(0).unit() shouldBe StandardUnit.PERCENT
        }
      }
      "should generate correct metric dimensions" - {
        s"should generate correct $permissionIdDimensionName dimension" in {
          forAll(getTooLargeMetricRequestGen) {
            case (TestWorld(_, permissionId, _, _, _), request) =>
              val dimensions = request.metricData().get(0).dimensions().asScala
              val dimensionNames = dimensions.map(_.name)
              dimensionNames should contain(permissionIdDimensionName)
              dimensions
                .filter(_.name == permissionIdDimensionName)
                .map(_.value()) should contain(
                permissionId
              )
          }
        }
        s"should generate correct $accessTypeDimensionName dimension" in {
          forAll(getTooLargeMetricRequestGen) {
            case (TestWorld(_, _, accessType, _, _), request) =>
              val dimensions =
                request.metricData().get(0).dimensions().asScala
              val dimensionNames = dimensions.map(_.name)
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
        s"should generate correct $accessSourceDimensionName dimension" in {
          forAll(getTooLargeMetricRequestGen) {
            case (TestWorld(_, _, _, accessSource, _), request) =>
              val dimensions =
                request.metricData().get(0).dimensions().asScala
              val dimensionNames = dimensions.map(_.name)
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
}
