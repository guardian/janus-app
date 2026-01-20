package models

import com.gu.janus.model.AwsAccount
import software.amazon.awssdk.arns.Arn

import java.time.Instant

/** Holds the data required to manage an IAM role that's part of a
  * [[com.gu.janus.model.ProvisionedRole]].
  *
  * @param roleArn
  *   ARN
  * @param provisionedRoleTagValue
  *   Corresponds to [[com.gu.janus.model.ProvisionedRole.iamRoleTag]]
  * @param friendlyName
  *   Name for display in Janus UI
  * @param description
  *   Description for display in Janus UI
  * @param account
  *   AWS account hosting the role
  */
case class IamRoleInfo(
    roleArn: Arn,
    provisionedRoleTagValue: String,
    friendlyName: Option[String],
    description: Option[String],
    account: AwsAccount
)

object IamRoleInfo {
  def apply(
      roleArnString: String,
      provisionedRoleTagValue: String,
      friendlyName: Option[String],
      description: Option[String],
      account: AwsAccount
  ): IamRoleInfo = IamRoleInfo(
    Arn.fromString(roleArnString),
    provisionedRoleTagValue,
    friendlyName,
    description,
    account
  )
}

/** A snapshot of a list of roles at the given time. */
case class IamRoleInfoSnapshot(
    roles: List[IamRoleInfo],
    timestamp: Instant
)

/** A failure and when it was recorded. */
case class FailureSnapshot(
    failure: String,
    timestamp: Instant
)

/** Status of [[IamRoleInfo]] data fetched from a single AWS account. */
case class AwsAccountIamRoleInfoStatus(
    roleSnapshot: Option[IamRoleInfoSnapshot],
    failureStatus: Option[FailureSnapshot]
)

object AwsAccountIamRoleInfoStatus {

  /** Convenience constructor for a successful data fetch. */
  def success(roleSnapshot: IamRoleInfoSnapshot): AwsAccountIamRoleInfoStatus =
    AwsAccountIamRoleInfoStatus(Some(roleSnapshot), failureStatus = None)

  /** Convenience constructor for when a data fetch fails. In this case we use
    * the last cached version of data (if it exists) and record the failure so
    * that we have a complete status record.
    */
  def failure(
      cachedRoleSnapshot: Option[IamRoleInfoSnapshot],
      failureStatus: FailureSnapshot
  ): AwsAccountIamRoleInfoStatus =
    AwsAccountIamRoleInfoStatus(cachedRoleSnapshot, Some(failureStatus))
}
