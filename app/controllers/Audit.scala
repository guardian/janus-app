package controllers

import aws.AuditTrailDB
import awscala.dynamodbv2._
import com.gu.googleauth.AuthAction
import com.gu.janus.model.JanusData
import logic.Date
import play.api.Logging
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents}


class Audit(janusData: JanusData, controllerComponents: ControllerComponents, authAction: AuthAction[AnyContent])
           (implicit dynamodDB: DynamoDB, assetsFinder: AssetsFinder)
  extends AbstractController(controllerComponents) with Logging {

  def byAccount(account: String) = authAction { implicit request =>
    val date = request.getQueryString("date") flatMap Date.parseDateStr getOrElse Date.today
    val (startDate, endDate) = Date.weekAround(date)
    logger.info(s"Getting logs for $account from $startDate to $endDate")
    val table = AuditTrailDB.getTable()
    val auditLogs = AuditTrailDB.getAccountLogs(table, account, startDate, endDate)
    val prevNextWeeks = Date.prevNextAuditWeeks(date)
    Ok(views.html.audit(auditLogs, Right(account), startDate, prevNextWeeks, request.user, janusData))
  }

  def byUser(username: String) = authAction { implicit request =>
    val date = request.getQueryString("date") flatMap Date.parseDateStr getOrElse Date.today
    val (startDate, endDate) = Date.weekAround(date)
    logger.info(s"Getting logs for $username from $startDate to $endDate")
    val table = AuditTrailDB.getTable()
    val auditLogs = AuditTrailDB.getUserLogs(table, username, startDate, endDate)
    val prevNextWeeks = Date.prevNextAuditWeeks(date)
    Ok(views.html.audit(auditLogs, Left(username), startDate, prevNextWeeks, request.user, janusData))
  }

  def changeUserDate(username: String) = authAction { implicit request =>
    val logDateStrOpt = for {
      submission <- request.body.asFormUrlEncoded
      logDateSubmission <- submission.get("audit-date")
      logDateStr <- logDateSubmission.headOption
    } yield logDateStr
    val param = logDateStrOpt.fold("")(s => s"?date=$s")
    SeeOther(s"/audit/user/$username$param")
  }

  def changeAccountDate(account: String) = authAction { implicit request =>
    val logDateStrOpt = for {
      submission <- request.body.asFormUrlEncoded
      logDateSubmission <- submission.get("audit-date")
      logDateStr <- logDateSubmission.headOption
    } yield logDateStr
    val param = logDateStrOpt.fold("")(s => s"?date=$s")
    SeeOther(s"/audit/account/$account$param")
  }
}
