package services

import aws.Clients.cloudwatchClient
import com.gu.janus.model.{JanusAccessType, PermissionType}
import models.AccessSource
import play.api.Logging
import services.MetricsService.*
import software.amazon.awssdk.services.cloudwatch.model.*
import software.amazon.awssdk.services.sts.model.PackedPolicyTooLargeException

import scala.jdk.CollectionConverters.*

class MetricsService(
    val stage: String
) extends Logging {

  private val namespace = s"/$stage/$stack/$app"

  def putDeniedRequest(
      permissionId: String,
      accessType: JanusAccessType
  ): Unit = {
    try {
      val deniedMetricRequest =
        getDeniedMetricRequest(permissionId, accessType)
      cloudwatchClient.putMetricData(deniedMetricRequest)
    } catch {
      case e: Throwable =>
        logger.error(s"Unable to send denied request metric", e);
    }
  }

  private[services] def getDeniedMetricRequest(
      permissionId: String,
      accessType: JanusAccessType
  ): PutMetricDataRequest = PutMetricDataRequest
    .builder()
    .namespace(namespace)
    .metricData(
      MetricDatum
        .builder()
        .metricName(deniedRequestMetricName)
        .value(1)
        .unit(StandardUnit.COUNT)
        .dimensions(
          Set(
            getPermissionIdDimension(permissionId),
            getAccessTypeDimension(accessType)
          ).toList.asJava
        )
        .build()
    )
    .build()

  def putFailedRequest(
      permissionId: String,
      permissionLabel: String,
      accessType: JanusAccessType,
      accessSource: AccessSource,
      permissionType: PermissionType
  ): Unit = {
    try {
      val failedMetricRequest =
        getFailedMetricRequest(
          permissionId,
          permissionLabel,
          permissionType,
          accessType,
          accessSource
        )
      cloudwatchClient.putMetricData(failedMetricRequest)
    } catch {
      case e: Throwable =>
        logger.error(s"Unable to send failed request metric", e);
    }
  }

  private[services] def getFailedMetricRequest(
      permissionId: String,
      permissionLabel: String,
      permissionType: PermissionType,
      accessType: JanusAccessType,
      accessSource: AccessSource
  ): PutMetricDataRequest = PutMetricDataRequest
    .builder()
    .namespace(namespace)
    .metricData(
      MetricDatum
        .builder()
        .metricName(failedRequestMetricName)
        .value(1)
        .unit(StandardUnit.COUNT)
        .dimensions(
          Set(
            getPermissionIdDimension(permissionId),
            getPermissionLabelDimension(permissionLabel),
            getPermissionTypeDimension(permissionType),
            getAccessTypeDimension(accessType),
            getAccessSourceDimension(accessSource)
          ).toList.asJava
        )
        .build()
    )
    .build()

  private def getPermissionIdDimension(permissionId: String) = Dimension
    .builder()
    .name(permissionIdDimensionName)
    .value(permissionId)
    .build()

  private def getPermissionLabelDimension(permissionLabel: String) = Dimension
    .builder()
    .name(permissionLabelDimensionName)
    .value(permissionLabel)
    .build()

  private def getPermissionTypeDimension(permissionType: PermissionType) =
    Dimension
      .builder()
      .name(permissionTypeDimensionName)
      .value(permissionType.serialised)
      .build()

  def putSuccessfulRequest(
      permissionId: String,
      permissionLabel: String,
      accessType: JanusAccessType,
      accessSource: AccessSource,
      permissionType: PermissionType,
      size: Int
  ): Unit = {
    try {
      val successfulMetricRequest =
        getSuccessfulMetricRequest(
          permissionId,
          permissionLabel,
          accessType,
          accessSource,
          permissionType,
          size
        )
      cloudwatchClient.putMetricData(successfulMetricRequest)
    } catch {
      case e: Throwable =>
        logger.error(s"Unable to send successful request metric", e);
    }
  }

  private[services] def getSuccessfulMetricRequest(
      permissionId: String,
      permissionLabel: String,
      accessType: JanusAccessType,
      accessSource: AccessSource,
      permissionType: PermissionType,
      size: Double
  ): PutMetricDataRequest = PutMetricDataRequest
    .builder()
    .namespace(namespace)
    .metricData(
      MetricDatum
        .builder()
        .metricName(successfulRequestMetricName)
        .value(size)
        .unit(StandardUnit.BYTES)
        .dimensions(
          Set(
            getPermissionIdDimension(permissionId),
            getPermissionLabelDimension(permissionLabel),
            getPermissionTypeDimension(permissionType),
            getAccessTypeDimension(accessType),
            getAccessSourceDimension(accessSource)
          ).toList.asJava
        )
        .build()
    )
    .build()

  private def getAccessSourceDimension(accessSource: AccessSource) = Dimension
    .builder()
    .name(accessSourceDimensionName)
    .value(accessSource.toString)
    .build()

  def putTooLargeRequest(
      permissionId: String,
      permissionLabel: String,
      accessType: JanusAccessType,
      accessSource: AccessSource,
      permissionType: PermissionType,
      e: PackedPolicyTooLargeException
  ): Unit = {
    try {
      val tooLargeMetricRequest =
        getTooLargeMetricRequest(
          permissionId,
          permissionLabel,
          accessType,
          accessSource,
          permissionType,
          e
        )
      cloudwatchClient.putMetricData(tooLargeMetricRequest)
    } catch {
      case e: Throwable =>
        logger.error(s"Unable to send successful request metric", e);
    }
  }

  def getTooLargeMetricRequest(
      permissionId: String,
      permissionLabel: String,
      accessType: JanusAccessType,
      accessSource: AccessSource,
      permissionType: PermissionType,
      e: PackedPolicyTooLargeException
  ): PutMetricDataRequest = {
    /*
     * It would be nice if we could get the value programmatically, but it appears it's only available in the message.
     */
    val size: Double = e.getMessage match {
      case TooLargePattern(size) => size.toDouble
      case _                     => 100
    }
    PutMetricDataRequest
      .builder()
      .namespace(namespace)
      .metricData(
        MetricDatum
          .builder()
          .metricName(tooLargeRequestMetricName)
          .value(size)
          .unit(StandardUnit.PERCENT)
          .dimensions(
            Set(
              getPermissionIdDimension(permissionId),
              getPermissionLabelDimension(permissionLabel),
              getPermissionTypeDimension(permissionType),
              getAccessTypeDimension(accessType),
              getAccessSourceDimension(accessSource)
            ).toList.asJava
          )
          .build()
      )
      .build()
  }

  private def getAccessTypeDimension(accessType: JanusAccessType) = Dimension
    .builder()
    .name(accessTypeDimensionName)
    .value(accessType.toString)
    .build()
}

object MetricsService {
  private[services] val stack: String = "security"
  private[services] val app: String = "janus"
  private[services] val permissionIdDimensionName = "permission-id"
  private[services] val permissionLabelDimensionName = "permission-label"
  private[services] val permissionTypeDimensionName = "permission-type"
  private[services] val accessTypeDimensionName = "access-type"
  private[services] val accessSourceDimensionName = "access-source"
  private[services] val successfulRequestMetricName = "successful-request"
  private[services] val tooLargeRequestMetricName = "too-large-request"
  private[services] val failedRequestMetricName = "failed-request"
  private[services] val deniedRequestMetricName = "denied-request"
  private val TooLargePattern = """.*?(\d+)%.*""".r
}
