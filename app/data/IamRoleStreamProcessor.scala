package data

import cats.effect.IO
import fs2.Stream
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.*

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class IamRoleStreamProcessor(
    cache: IamRoleCache,
    iamClient: IamClient,
    janusRoleTagKey: String = "janus:permission"
) {

  def startPolling: Stream[IO, Unit] = {
    Stream
      .awakeEvery[IO](1.minutes)
      .evalMap(_ => IO(fetchAndGroupRoles()))
      .evalMap(roles => IO(cache.update(roles)))
  }

  private def fetchAndGroupRoles(): Map[String, List[IamRoleInfo]] = {
    val response = iamClient.listRoles()
    val allRoles = response.roles().asScala.toList

    allRoles
      .flatMap { role =>
        val tags = fetchRoleTags(role.roleName())
        tags.get(janusRoleTagKey).map { tagValue =>
          IamRoleInfo(
            role.roleName(),
            role.arn(),
            tags
          ) -> tagValue
        }
      }
      .groupMap(_._2)(_._1)
  }

  private def fetchRoleTags(roleName: String): Map[String, String] = {
    val request = ListRoleTagsRequest.builder().roleName(roleName).build()
    val response = iamClient.listRoleTags(request)
    response.tags().asScala.map(tag => tag.key() -> tag.value()).toMap
  }
}
