package aws

import cats.effect.IO
import cats.syntax.traverse.*
import com.gu.janus.model.AwsAccount
import data.ProvisionedRoleCache
import models.IamRoleInfo
import play.api.Logging
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.iam.model.{
  ListRoleTagsRequest,
  ListRolesRequest,
  Role,
  Tag
}

import java.time.{Clock, Instant}
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*

/** Fetches AWS IAM roles that have a [[com.gu.janus.model.ProvisionedRole]] tag
  * attached to them. Role data is fetched from a single AWS account.
  *
  * @param account
  *   AWS account to search for appropriate IAM roles
  * @param iam
  *   IAM client that will do the searching
  * @param cache
  *   Holds any data found
  * @param clock
  *   We attach a timestamp to data found so that we can see how fresh it is
  */
class SingleAccountRoleFetcher(
    account: AwsAccount,
    iam: IamAsyncClient,
    cache: ProvisionedRoleCache,
    clock: Clock = Clock.systemUTC()
) extends Logging {

  /** All roles that are discoverable by Janus have this path. */
  private val discoverableRolePath: String = "/gu/janus/discoverable/"
  private val roleListRequestBuilder =
    ListRolesRequest.builder.pathPrefix(discoverableRolePath)

  /** Tag attached to a role that is part of a
    * [[com.gu.janus.model.ProvisionedRole]].
    */
  private val provisionedRoleTag: String = "gu:janus:permission"

  /** This tag is optional for IAM roles that are part of a
    * [[com.gu.janus.model.ProvisionedRole]] but it helps in the UI.
    */
  private val friendlyNameTag: String = "gu:janus:name"

  /** This tag is optional for IAM roles that are part of a
    * [[com.gu.janus.model.ProvisionedRole]] but it helps in the UI.
    */
  private val descriptionTag: String = "gu:janus:description"

  def fetchDataAndUpdateCache(): IO[Unit] =
    (for {
      _ <- IO(
        logger.info(
          s"Fetching provisioned roles from account ${account.name}..."
        )
      )
      roles <- fetchRoles()
      // We have to make a separate call to fetch tags because they aren't given in the list-roles response
      roleTags <- fetchRoleTags(roles)
      groupedRoles <- IO(
        IamRoleInfo.groupIamRolesByTag(
          account,
          roleTags,
          provisionedRoleTag,
          friendlyNameTag,
          descriptionTag,
          Instant.now(clock)
        )
      )
      _ <- groupedRoles.toList.traverse { case (tagValue, roles) =>
        IO(cache.update(tagValue, account, roles))
      }
      _ <- IO(
        logger.info(
          s"Fetched ${groupedRoles.values.flatten.size} provisioned roles from account '${account.name}'."
        )
      )
    } yield ()).handleErrorWith { err =>
      IO(
        logger.error(
          s"Failed to update provisioned roles for account ${account.name}",
          err
        )
      )
    }

  /** Shut down all resources */
  def close(): IO[Unit] = IO(iam.close())

  private def fetchRoles(): IO[List[Role]] = {
    // Page through results as we don't know how many discoverable roles there will be per account
    def fetchPage(
        nextToken: Option[String],
        acc: List[Role]
    ): IO[List[Role]] = {
      val request =
        nextToken
          .map(roleListRequestBuilder.marker)
          .getOrElse(roleListRequestBuilder)
          .build()

      toIO(iam.listRoles(request)).flatMap { response =>
        val roles = acc ++ response.roles().asScala.toList
        Option(response.marker()) match {
          case Some(marker) if response.isTruncated =>
            fetchPage(Some(marker), roles)
          case _ => IO.pure(roles)
        }
      }
    }

    fetchPage(None, List.empty)
  }

  private def fetchRoleTags(roles: List[Role]): IO[Map[Role, Set[Tag]]] =
    roles
      .traverse { role => fetchRoleTags(role).map(tags => role -> tags) }
      .map(_.toMap)

  private def fetchRoleTags(role: Role): IO[Set[Tag]] = {
    val request = ListRoleTagsRequest.builder.roleName(role.roleName).build()
    // No need to page through results as we don't expect many tags per role
    toIO(iam.listRoleTags(request))
      .map(response => response.tags().asScala.toSet)
  }

  private def toIO[A](
      completableFuture: => java.util.concurrent.CompletableFuture[A]
  ): IO[A] =
    IO.fromFuture(IO(completableFuture.asScala))
}
