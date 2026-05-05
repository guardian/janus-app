package aws

import aws.Clients.cloudwatchClient
import aws.CloudWatchMetrics.{
  DeniedRequest,
  FailedRequest,
  SuccessfulRequestPolicySizeMetric
}
import com.gu.janus.model.{JanusAccessType, Permission}
import models.{AccessSource, DeveloperPolicy}
import play.api.Logging
import software.amazon.awssdk.services.cloudwatch.model.*

import scala.jdk.CollectionConverters.*

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

class CloudWatch(val stage: String, val stack: String, val app: String)
    extends Logging {

  private val namespace = s"/$stage/$stack/$app"

  def putDeniedRequest(
      developerPolicies: Set[DeveloperPolicy],
      permissionId: String,
      accessType: JanusAccessType
  ): Unit = put(DeniedRequest, developerPolicies, permissionId, accessType, 1)

  def putFailedRequest(
      developerPolicies: Set[DeveloperPolicy],
      permissionId: String,
      accessType: JanusAccessType
  ): Unit = put(FailedRequest, developerPolicies, permissionId, accessType, 1)

  def putSuccessfulRequestPolicySizeMetric(
      developerPolicies: Set[DeveloperPolicy],
      permission: Permission,
      accessSource: AccessSource,
      permissionId: String,
      accessType: JanusAccessType,
      value: Int
  ): Unit = put(
    SuccessfulRequestPolicySizeMetric,
    developerPolicies,
    permissionId,
    accessType,
    value,
    Some(permission),
    Some(accessSource)
  )

  private def convertToDimensions(
      developerPolicies: Set[DeveloperPolicy],
      permissionId: String,
      accessType: JanusAccessType,
      permissionMaybe: Option[Permission] = None,
      accessSourceMaybe: Option[AccessSource] = None
  ): Set[Dimension] =
    (developerPolicies.zipWithIndex.flatMap { case (dp, i) =>
      Iterable(
        s"account-$i" -> dp.account.name,
        s"grant-id-$i" -> dp.policyGrantId
      )
    }
      + ("permissionId" -> permissionId)
      + ("accessType" -> accessType.toString)
      ++ permissionMaybe.map(p => "permissionLabel" -> p.label)
      ++ accessSourceMaybe.map(a => "accessSource" -> a.toString))
      .map { case (n, v) =>
        Dimension.builder().name(n).value(v).build()
      }

  private def put(
      metric: CloudWatchMetric,
      developerPolicies: Set[DeveloperPolicy],
      permissionId: String,
      accessType: JanusAccessType,
      value: Int,
      permissionMaybe: Option[Permission] = None,
      accessSourceMaybe: Option[AccessSource] = None
  ): Unit = try {
    val dimensions = convertToDimensions(
      developerPolicies,
      permissionId,
      accessType,
      permissionMaybe,
      accessSourceMaybe
    )

    val putMetricDataRequest =
      CloudWatch.buildPutMetricRequest(namespace, metric, value, dimensions)
    cloudwatchClient.putMetricData(putMetricDataRequest)
  } catch {
    case e: Throwable =>
      logger.error("Unable to send metrics", e);
  }
}

object CloudWatch {
  def buildPutMetricRequest(
      namespace: String,
      metric: CloudWatchMetric,
      value: Int = 1,
      dimensions: Iterable[Dimension] = Nil
  ): PutMetricDataRequest = {
    PutMetricDataRequest
      .builder()
      .namespace(namespace)
      .metricData(
        MetricDatum
          .builder()
          .metricName(metric.name)
          .value(value.toDouble)
          .unit(metric.unit)
          .dimensions(dimensions.toList.asJava)
          .build()
      )
      .build()
  }
}
