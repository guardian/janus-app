import play.sbt.PlayRunHook
import sbt._

import scala.sys.process.Process

object DockerComposeHook {
  def apply(base: File): PlayRunHook = {
    object DockerComposeHook extends PlayRunHook {
      var process: Option[Process] = None

      val up = "docker compose up -d"
      val down = "docker compose down"

      override def afterStarted(): Unit = {
        process = Some(
          Process(up, base / "local-dev").run
        )
      }

      override def afterStopped(): Unit = {
        Process(down, base / "local-dev").!
        process.foreach(p => p.destroy())
        process = None
      }
    }
    DockerComposeHook
  }
}
