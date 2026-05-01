package services

import aws.Clients.cloudwatchClient
import com.gu.janus.model.JanusAccessType
import models.AccessSource
import play.api.Logging
import services.MetricsService.*
import software.amazon.awssdk.services.cloudwatch.model.*
import software.amazon.awssdk.services.sts.model.PackedPolicyTooLargeException

import scala.jdk.CollectionConverters.*

class MetricsService(
    val stage: String,
    val stack: String = "security",
    val app: String = "janus"
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

  private def putFailedRequest(
      permissionId: String,
      accessType: JanusAccessType,
      accessSource: AccessSource
  ): Unit = {
    try {
      val failedMetricRequest =
        getFailedMetricRequest(permissionId, accessType, accessSource)
      cloudwatchClient.putMetricData(failedMetricRequest)
    } catch {
      case e: Throwable =>
        logger.error(s"Unable to send failed request metric", e);
    }
  }

  private[services] def getFailedMetricRequest(
      permissionId: String,
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

  def putSuccessfulRequest(
      permissionId: String,
      accessType: JanusAccessType,
      accessSource: AccessSource,
      size: Int
  ): Unit = {
    try {
      val successfulMetricRequest =
        getSuccessfulMetricRequest(
          permissionId,
          accessType,
          accessSource,
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
      accessType: JanusAccessType,
      accessSource: AccessSource,
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

  def putExceptionOnRequest(
      permissionId: String,
      accessType: JanusAccessType,
      accessSource: AccessSource,
      e: Throwable
  ): Nothing = e match {
    case e: PackedPolicyTooLargeException =>
      this.putTooLargeRequest(
        permissionId,
        accessType,
        accessSource,
        e
      )
      throw e
    case _ =>
      this.putFailedRequest(
        permissionId,
        accessType,
        accessSource
      )
      throw e
  }

  private def putTooLargeRequest(
      permissionId: String,
      accessType: JanusAccessType,
      accessSource: AccessSource,
      e: PackedPolicyTooLargeException
  ): Unit = {
    try {
      val tooLargeMetricRequest =
        getTooLargeMetricRequest(
          permissionId,
          accessType,
          accessSource,
          e
        )
      cloudwatchClient.putMetricData(tooLargeMetricRequest)
    } catch {
      case e: Throwable =>
        logger.error(s"Unable to send successful request metric", e);
    }
  }

  private[services] def getTooLargeMetricRequest(
      permissionId: String,
      accessType: JanusAccessType,
      accessSource: AccessSource,
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
  private[services] val permissionIdDimensionName = "permission-id"
  private[services] val accessTypeDimensionName = "access-type"
  private[services] val accessSourceDimensionName = "access-source"
  private[services] val successfulRequestMetricName = "successful-request"
  private[services] val tooLargeRequestMetricName = "too-large-request"
  private[services] val failedRequestMetricName = "failed-request"
  private[services] val deniedRequestMetricName = "denied-request"
  private val TooLargePattern = """.*?(\d+)%.*""".r
}
