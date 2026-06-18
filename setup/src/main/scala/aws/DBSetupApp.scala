package aws

import play.api.Logging
import software.amazon.awssdk.services.dynamodb.model.*

object DBSetupApp extends Logging {

  def main(args: Array[String]): Unit = {
    try {
      new AuditTrailDBSetup().destroyTable()(Clients.localDb)
    } catch {
      case e: Throwable =>
        logger.info(s"Audit trail table delete skipped with ${e.getMessage}")
    }
    try {
      new PasskeyChallengeDBSetup().destroyTable()(Clients.localDb)
    } catch {
      case e: Throwable =>
        logger.info(
          s"Passkey challenge table delete skipped with ${e.getMessage}"
        )
    }
    try {
      new PasskeyDBSetup().destroyTable()(Clients.localDb)
    } catch {
      case e: Throwable =>
        logger.info(s"Passkey table delete skipped with ${e.getMessage}")
    }
    new AuditTrailDBSetup().createTable()(Clients.localDb)
    new PasskeyChallengeDBSetup().createTable()(Clients.localDb)
    new PasskeyDBSetup().createTable()(Clients.localDb)
  }

}
