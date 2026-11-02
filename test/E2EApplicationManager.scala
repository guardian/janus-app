import com.gu.janus.model.JanusData

import java.util.UUID
import scala.util.Try

/** Tracks test applications by ID and controls their lifetime. Lifecycle
  * operations are serialised; browser traffic is independent. The applications
  * share a JVM, but each has its own application state and port.
  */
final class E2EApplicationManager extends AutoCloseable {
  private var instances = Map.empty[String, E2EApplication]
  private var closed = false

  def create(data: JanusData): (String, String) = synchronized {
    require(!closed, "E2E application manager is stopping")
    val id = UUID.randomUUID().toString
    val application = E2EApplication.start(data)
    instances = instances.updated(id, application)
    id -> application.baseURL
  }

  def delete(id: String): Unit = synchronized {
    instances.get(id).foreach(_.stop())
    instances = instances.removed(id)
  }

  override def close(): Unit = synchronized {
    closed = true
    val failures =
      instances.values.toList.flatMap(application =>
        Try(application.stop()).failed.toOption
      )
    instances = Map.empty
    failures.headOption.foreach { error =>
      failures.tail.foreach(error.addSuppressed)
      throw error
    }
  }
}
