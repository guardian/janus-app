package controllers

import aws.AuditTrailDB
import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import logic.Date
import play.api.mvc.{
  AbstractController,
  Action,
  AnyContent,
  ControllerComponents
}
import play.api.{Logging, Mode}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

class Audit(
    janusData: JanusData,
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent]
)(using dynamodDB: DynamoDbClient, mode: Mode, assetsFinder: AssetsFinder)
    extends AbstractController(controllerComponents)
    with Logging {

  def byAccount(account: String): Action[AnyContent] = authAction {
    implicit request =>
      val date = request.getQueryString(
        "date"
      ) flatMap Date.parseUtcDateStr getOrElse Date.todayUtc
      val (startDate, endDate) = Date.weekAround(date)
      logger.info(s"Getting logs for $account from $startDate to $endDate")
      val auditLogs = AuditTrailDB.getAccountLogs(account, startDate, endDate)
      val prevNextWeeks = Date.prevNextAuditWeeks(date)
      Ok(
        views.html.audit(
          auditLogs,
          Right(account),
          startDate,
          prevNextWeeks,
          request.user,
          janusData
        )
      )
  }

  def byUser(username: String): Action[AnyContent] = authAction {
    implicit request =>
      val date = request.getQueryString(
        "date"
      ) flatMap Date.parseUtcDateStr getOrElse Date.todayUtc
      val (startDate, endDate) = Date.weekAround(date)
      logger.info(s"Getting logs for $username from $startDate to $endDate")
      val auditLogs = AuditTrailDB.getUserLogs(username, startDate, endDate)
      val prevNextWeeks = Date.prevNextAuditWeeks(date)
      Ok(
        views.html.audit(
          auditLogs,
          Left(username),
          startDate,
          prevNextWeeks,
          request.user,
          janusData
        )
      )
  }

  def changeUserDate(username: String): Action[AnyContent] = authAction {
    implicit request =>
      val logDateStrOpt = for {
        submission <- request.body.asFormUrlEncoded
        logDateSubmission <- submission.get("audit-date")
        logDateStr <- logDateSubmission.headOption
      } yield logDateStr
      val param = logDateStrOpt.fold("")(s => s"?date=$s")
      SeeOther(s"/audit/user/$username$param")
  }

  def changeAccountDate(account: String): Action[AnyContent] = authAction {
    implicit request =>
      val logDateStrOpt = for {
        submission <- request.body.asFormUrlEncoded
        logDateSubmission <- submission.get("audit-date")
        logDateStr <- logDateSubmission.headOption
      } yield logDateStr
      val param = logDateStrOpt.fold("")(s => s"?date=$s")
      SeeOther(s"/audit/account/$account$param")
  }
}
