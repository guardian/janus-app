package models

import software.amazon.awssdk.services.iam.model.{Role, Tag}

case class IamRoleInfo(
    roleName: String,
    roleArn: String,
    tags: Map[String, String]
)

object IamRoleInfo {

  def groupIamRolesByTag(
      roleTags: Map[Role, Set[Tag]],
      groupingTagKey: String
  ): Map[String, List[IamRoleInfo]] = {
    roleTags.toList
      .flatMap { (role, tags) =>
        tags.find(_.key == groupingTagKey).map { tag =>
          IamRoleInfo(
            role.roleName,
            role.arn,
            tags.map(tag => tag.key -> tag.value).toMap
          ) -> tag.value
        }
      }
      .groupMap(_._2)(_._1)
  }
}
