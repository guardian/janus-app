package aws

import cats.effect.IO
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.*

import scala.jdk.CollectionConverters.*

/** For managing calls to AWS IAM services and transforming results. */
object Iam {

  def listPolicies(
      iam: IamClient,
      request: ListPoliciesRequest
  ): IO[List[Policy]] =
    // Page through results as we don't know how many there will be per account
    IO.blocking(
      iam
        .listPoliciesPaginator(request)
        .asScala
        .foldLeft(List.empty[Policy])((acc, response) =>
          acc ++ response.policies.asScala.toList
        )
    )

  def listPolicyTags(iam: IamClient, policy: Policy): IO[Set[Tag]] = {
    val request = ListPolicyTagsRequest.builder.policyArn(policy.arn).build()
    // No need to page through results as we don't expect many tags per policy
    IO.blocking(iam.listPolicyTags(request).tags().asScala.toSet)
  }
}
