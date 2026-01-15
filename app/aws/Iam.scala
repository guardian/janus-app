package aws

import cats.effect.IO
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.{
  ListRoleTagsRequest,
  ListRolesRequest,
  Role,
  Tag
}

import scala.jdk.CollectionConverters.*

/** For managing calls to AWS IAM services and transforming results. */
object Iam {

  def listRoles(iam: IamClient, request: ListRolesRequest): IO[List[Role]] =
    // Page through results as we don't know how many discoverable roles there will be per account
    IO.blocking(
      iam
        .listRolesPaginator(request)
        .asScala
        .foldLeft(List.empty[Role])((acc, response) =>
          acc ++ response.roles().asScala
        )
    )

  def listRoleTags(iam: IamClient, role: Role): IO[Set[Tag]] = {
    val request = ListRoleTagsRequest.builder.roleName(role.roleName).build()
    // No need to page through results as we don't expect many tags per role
    IO.blocking(iam.listRoleTags(request).tags().asScala.toSet)
  }
}
