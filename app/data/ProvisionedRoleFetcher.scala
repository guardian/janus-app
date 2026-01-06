package data

import cats.effect.IO
import fs2.Stream
import models.IamRoleInfo
import play.api.inject.ApplicationLifecycle
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.*

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class ProvisionedRoleFetcher(
    lifecycle: ApplicationLifecycle,
    iam: IamClient,
    cache: ProvisionedRoleCache,
    fetchRate: FiniteDuration,
    isJanusRoleTag: String = "janus:discoverable",
    janusRoleTag: String = "janus:permission"
) {

  import cats.effect.unsafe.implicits.global
  lifecycle.addStopHook(startPolling.compile.drain.unsafeRunCancelable())

  private def startPolling: Stream[IO, Unit] = {
    Stream
      .awakeEvery[IO](fetchRate)
      .evalMap(_ => IO(fetchAndGroupRoles()))
      .evalMap(roles => IO(cache.update(roles)))
  }

  // TODO: fetch across all accounts
  private def fetchAndGroupRoles(): Map[String, List[IamRoleInfo]] = {
    // Fetches a page of roles rather than a complete set
    val response = iam.listRoles()
    val allRoles = response.roles().asScala.toList

    allRoles
      .flatMap { role =>
        val tags = fetchRoleTags(role.roleName())
        // Only include roles that have the isJanusRole tag
        if (tags.contains(isJanusRoleTag)) {
          // Group by janusRole tag value if it exists
          tags.get(janusRoleTag).map { tagValue =>
            IamRoleInfo(
              role.roleName(),
              role.arn(),
              tags
            ) -> tagValue
          }
        } else None
      }
      .groupMap(_._2)(_._1)
  }

  // TODO fetch all tags for all roles at once?
  private def fetchRoleTags(roleName: String): Map[String, String] = {
    val request = ListRoleTagsRequest.builder().roleName(roleName).build()
    val response = iam.listRoleTags(request)
    response.tags().asScala.map(tag => tag.key() -> tag.value()).toMap
  }
}
