package models

import com.gu.janus.model.AwsAccount
import software.amazon.awssdk.services.iam.model.{Role, Tag}

import java.time.Instant

/** Holds the data required to manage an IAM role that forms part of a
  * [[com.gu.janus.model.ProvisionedRole]].
  *
  * @param roleArn
  *   ARN
  * @param account
  *   Host account
  * @param friendlyName
  *   Tagged name for display in Janus UI
  * @param description
  *   Tagged description for display in Janus UI
  * @param lastFetched
  *   Helps to troubleshoot freshness issues
  */
case class IamRoleInfo(
    roleArn: String,
    account: AwsAccount,
    friendlyName: Option[String],
    description: Option[String],
    lastFetched: Instant
)

object IamRoleInfo {

  /** Sorts out role and tag data so that we get a map of grouping-tag values to
    * roles that have a tag with the grouping-tag name and that value.
    *
    * @param account
    *   Source of data
    * @param roleTags
    *   A map of role to all the tags attached to the role
    * @param groupingTagKey
    *   Name of the tag whose values are being used to group by. We ignore roles
    *   that haven't got that tag.
    * @param friendlyNameTagKey
    *   Name of the optional tag giving a friendly name for the role to show in
    *   a UI
    * @param descriptionTagKey
    *   Name of the optional tag giving a description for the role to show in a
    *   UI
    * @param lastFetched
    *   Useful for troubleshooting freshness issues
    * @return
    *   map of grouping-tag values to roles
    */
  def groupIamRolesByTag(
      account: AwsAccount,
      roleTags: Map[Role, Set[Tag]],
      groupingTagKey: String,
      friendlyNameTagKey: String,
      descriptionTagKey: String,
      lastFetched: Instant
  ): Map[String, List[IamRoleInfo]] = {
    roleTags.toList
      .flatMap { (role, tags) =>
        tags.find(_.key == groupingTagKey).map { tag =>
          IamRoleInfo(
            role.arn,
            account,
            friendlyName = tags.find(_.key == friendlyNameTagKey).map(_.value),
            description = tags.find(_.key == descriptionTagKey).map(_.value),
            lastFetched
          ) -> tag.value
        }
      }
      .groupMap(_._2)(_._1)
  }
}
