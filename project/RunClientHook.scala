import play.sbt.PlayRunHook
import sbt._

import scala.sys.process.Process

/** Frontend build play run hook.
 * https://www.playframework.com/documentation/2.8.x/SBTCookbook
 */
object RunClientHook {
  def apply(base: File): PlayRunHook = {
    object ClientBuildHook extends PlayRunHook {
      var process: Option[Process] = None

      val install = "npm install"
      val run = "npm run start"

      /**
       * Executed before play run.
       *
       * Run npm install if the node modules directory does not exist.
       */
      override def beforeStarted(): Unit = {
        if (!(base / "frontend" / "node_modules").exists())
          Process(install, base / "frontend").!
      }

      /**
       * Runs npm start script after play run
       */
      override def afterStarted(): Unit = {
        process = Some(
          Process(run, base / "frontend").run
        )
      }

      /** Executed after play run stop. Cleanup frontend execution processes.
       */
      override def afterStopped(): Unit = {
        process.foreach(p => p.destroy())
        process = None
      }
    }
    ClientBuildHook
  }
}