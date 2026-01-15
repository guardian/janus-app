package aws

import cats.effect.IO
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.iam.model.{
  ListRoleTagsRequest,
  ListRolesRequest,
  Role,
  Tag
}

import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*

/** For managing calls to AWS IAM services and transforming results. */
object Iam {

  def listRoles(
      iam: IamAsyncClient,
      requestBuilder: ListRolesRequest.Builder
  ): IO[List[Role]] = {
    // Page through results as we don't know how many discoverable roles there will be per account
    def fetchPage(
        nextToken: Option[String],
        acc: List[Role]
    ): IO[List[Role]] = {
      val request =
        nextToken
          .map(requestBuilder.marker)
          .getOrElse(requestBuilder)
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

  def listRoleTags(iam: IamAsyncClient, role: Role): IO[Set[Tag]] = {
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
