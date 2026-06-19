package aws

import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import play.api.Logging

/** Use with create, destroy, or recreate
  */
object DBSetupApp extends Logging {

  def main(args: Array[String]): Unit = {
    val (create, destroy) = parseArgs(args.toList)
    if (destroy) {
      val audit = try {
        AuditTrailDBSetup.destroyTable()(Clients.localDb)
        logger.info(
          s"Audit trail table deleted"
        )
        true
      } catch {
        case e: ResourceNotFoundException =>
          logger.warn(
            s"Audit trail table delete skipped with ResourceNotFoundException"
          )
          true
        case e: Throwable =>
          logger.error(s"Audit table delete failed: ${e.getMessage}")
          false
      }
      val passkeyChallenge = try {
        PasskeyChallengeDBSetup.destroyTable()(Clients.localDb)
        logger.info(
          s"Passkey challenge table deleted"
        )
        true
      } catch {
        case e: ResourceNotFoundException =>
          logger.warn(
            s"Passkey challenge table delete skipped with ResourceNotFoundException"
          )
          true
        case e: Throwable =>
          logger.error(
            s"Passkey challenge table delete failed: ${e.getMessage}"
          )
          false
      }
      val passkey = try {
        PasskeyDBSetup.destroyTable()(Clients.localDb)
        logger.info(
          s"Passkey table deleted"
        )
        true
      } catch {
        case e: ResourceNotFoundException =>
          logger.warn(
            s"Passkey table delete skipped with ResourceNotFoundException"
          )
          true
        case e: Throwable =>
          logger.error(s"Passkey table delete failed: ${e.getMessage}")
          false
      }
      if (!passkey || !passkeyChallenge || !audit) {
        System.exit(1)
      }
    }

    if (create) {
      val audit = try {
        AuditTrailDBSetup.createTable()(Clients.localDb)
        logger.info(
          s"Audit trail table created"
        )
        true
      } catch {
        case e: Throwable =>
          logger.error(
            s"Audit trail table create failed: ${e.getMessage}"
          )
          false
      }
      val passkeyChallenge = try {
        PasskeyChallengeDBSetup.createTable()(Clients.localDb)
        logger.info(
          s"Passkey challenge table created"
        )
        true
      } catch {
        case e: Throwable =>
          logger.error(
            s"Passkey challenge table create failed: ${e.getMessage}"
          )
          false
      }
      val passkey = try {
        PasskeyDBSetup.createTable()(Clients.localDb)
        logger.info(s"Passkey table created")
        true
      } catch {
        case e: Throwable =>
          logger.error(s"Passkey table create failed: ${e.getMessage}")
          false
      }
      if (!passkey || !passkeyChallenge || !audit) {
        System.exit(1)
      }
    }
  }

  private def parseArgs(args: List[String]): (Boolean, Boolean) = {
    val createString = "create"
    val destroyString = "destroy"
    val recreateString = "recreate"
    val knownArgs = List(createString, destroyString, recreateString)
    val create = args.contains(createString) || args.contains(recreateString)
    val destroy = args.contains(destroyString) || args.contains(recreateString)
    val unknownArgs = args.filterNot(a => knownArgs.contains(a))
    if (unknownArgs.nonEmpty) {
      throw new Exception(s"Unknown args: ${unknownArgs.mkString(", ")}")
    }
    if (!create && !destroy) {
      logger.error("No action requested")
      System.exit(1)
    }
    (create, destroy)
  }

}
