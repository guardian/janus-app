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

  // This is necessary to get the policy description, which isn't populated in the listPolicies call
  def getPolicyDetails(iam: IamClient, summary: Policy): IO[Policy] = {
    val request = GetPolicyRequest.builder.policyArn(summary.arn).build()
    IO.blocking(iam.getPolicy(request).policy())
  }
}
