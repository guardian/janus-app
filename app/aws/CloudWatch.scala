package aws

import aws.Clients.cloudwatchAsyncClient
import play.api.Logging
import software.amazon.awssdk.services.cloudwatch.model.*

import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*

sealed class CloudWatchMetric(val name: String, val unit: StandardUnit)

object CloudWatchMetrics {
  case object DeniedRequest
      extends CloudWatchMetric("denied-request", StandardUnit.COUNT)

  case object FailedRequest
      extends CloudWatchMetric("failed-request", StandardUnit.COUNT)

  case object SuccessfulRequestPolicySizeMetric
      extends CloudWatchMetric("packed-policy-size", StandardUnit.BYTES)

  def allValues: List[CloudWatchMetric] =
    List(
      DeniedRequest,
      FailedRequest,
      SuccessfulRequestPolicySizeMetric
    )
}

object CloudWatch extends Logging {

  private val namespace = "janus"

  def put(
      metric: CloudWatchMetric,
      dimensionStrings: Map[String, String],
      value: Int = 1
  ): Unit = {
    val dimensions = dimensionStrings.map { case (n, v) =>
      Dimension.builder().name(n).value(v).build()
    }.toList
    val putMetricDataRequest =
      buildPutMetricRequest(metric, value, dimensions)
    cloudwatchAsyncClient
      .putMetricData(putMetricDataRequest)
      .asScala // Future, but we fire and forget
  }

  private[aws] def buildPutMetricRequest(
      metric: CloudWatchMetric,
      value: Int = 1,
      dimensions: List[Dimension] = List.empty
  ) = {
    PutMetricDataRequest
      .builder()
      .namespace(namespace)
      .metricData(
        MetricDatum
          .builder()
          .metricName(metric.name)
          .value(value.toDouble)
          .unit(metric.unit)
          .dimensions(dimensions.asJava)
          .build()
      )
      .build()
  }
}
