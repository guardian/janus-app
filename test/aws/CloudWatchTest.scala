package aws

import aws.CloudWatch.buildPutMetricRequest
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.*
import software.amazon.awssdk.services.cloudwatch.model.{
  Dimension,
  PutMetricDataRequest
}
import scala.jdk.CollectionConverters._

class CloudWatchTest
    extends AnyFreeSpec
    with Matchers
    with ScalaCheckPropertyChecks {

  "buildPutMetricRequest" - {
    "builds a put metric request with all correct values" in {

      forAll(putMetricGen) { case ((m, v, d), r) =>
        r.metricData().size() shouldBe 1
        val metricData = r.metricData().get(0)
        val dimensions = metricData.dimensions().asScala.toList
        metricData.metricName() shouldBe m.name
        metricData.unit() shouldBe m.unit
        metricData.value().toInt shouldBe v
        dimensions shouldBe d
      }
    }
  }

  def dimensionsGen: Gen[List[Dimension]] = for {
    dimensionName <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    dimensionValue <- Gen.alphaNumStr.suchThat(_.nonEmpty)
  } yield List(
    Dimension.builder().name(dimensionName).value(dimensionValue).build()
  )

  def putMetricGen
      : Gen[((CloudWatchMetric, Int, List[Dimension]), PutMetricDataRequest)] =
    for {
      metric <- Gen.oneOf(CloudWatchMetrics.allValues)
      value <- Gen.choose(1, 10)
      dimensions <- dimensionsGen
    } yield (
      (metric, value, dimensions),
      buildPutMetricRequest(metric, value, dimensions)
    )
}
