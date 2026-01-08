// TODO move to aws
package data

import cats.effect.IO
import fs2.Stream
import models.IamRoleInfo
import play.api.Logging
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.*

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class ProvisionedRoleFetcher(
    iam: IamClient,
    cache: ProvisionedRoleCache,
    fetchRate: FiniteDuration
) extends Logging {

  /** All roles that are discoverable by Janus have this path. */
//  private val discoverableRolePath: String = "/gu/janus/discoverable/"
  private val discoverableRolePath: String = "/gu-janus-discoverable/"

  /** Tag attached to a role that is part of a
    * [[com.gu.janus.model.ProvisionedRole]].
    */
  private val provisionedRoleTag: String = "gu:janus:permission"

  // TODO polling shouldn't stack up - so if one is missed it should be lost - test this
  def startPolling(): Stream[IO, Unit] =
    Stream
      // do first fetch immediately
      .emit(())
      // then periodically
      .append(Stream.awakeEvery[IO](fetchRate))
      .evalTap(_ => IO(logger.info("Refreshing provisioned role cache...")))
      .evalMap(_ => IO.blocking(fetchRoles()))
      .evalMap(roles => IO.blocking(fetchRoleTags(roles)))
      .evalMap(roleTags =>
        IO(IamRoleInfo.groupIamRolesByTag(roleTags, provisionedRoleTag))
      )
      .evalMap(roles => IO(cache.update(roles)))
      .evalTap(_ =>
        IO(
          logger.info(
            s"Refresh of provisioned role cache complete.  Now holds ${cache.getAll.size} entries"
          )
        )
      )
      .handleErrorWith { err =>
        Stream.eval(
          IO(logger.error("Failed to refresh provisioned role cache", err))
        )
      }

  // TODO: fetch across all accounts
  private def fetchRoles(): List[Role] = {
    val request =
      ListRolesRequest.builder.pathPrefix(discoverableRolePath).build()
    iam
      .listRolesPaginator(request)
      .asScala
      .foldLeft(List.empty[Role])((acc, page) =>
        acc ++ page.roles.asScala.toList
      )
  }

  private def fetchRoleTags(roles: List[Role]): Map[Role, Set[Tag]] =
    roles.map { role =>
      val request = ListRoleTagsRequest.builder.roleName(role.roleName).build()
      role -> iam
        .listRoleTags(request)
        .tags()
        .asScala
        .toSet
    }.toMap
}
