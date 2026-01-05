package data

import com.github.benmanes.caffeine.cache.Caffeine
import com.gu.janus.model.AwsAccount
import models.IamRoleInfo

import scala.jdk.CollectionConverters.*

/** Cache holding the role data for IAM roles that are part of
  * [[com.gu.janus.model.ProvisionedRole]]s granted in
  * [[com.gu.janus.model.JanusData]].
  */
class ProvisionedRoleCache {
  private val cache =
    Caffeine.newBuilder
      .maximumSize(1000)
      .build[String, Map[AwsAccount, List[IamRoleInfo]]]

  /** Updates the cache with the roles from the given account that have a
    * provisioned role tag attached to them with the given value. The previous
    * roles for that account will be overwritten.
    */
  def update(
      tagValue: String,
      account: AwsAccount,
      roles: List[IamRoleInfo]
  ): Unit =
    cache
      .asMap()
      .compute(
        tagValue,
        (_, existing) => {
          // The map values are initially null
          val safeMap = Option(existing).getOrElse(Map.empty)
          safeMap + (account -> roles)
        }
      )

  /** Gives the cached roles from all accounts that have the given value of the
    * provisioned role tag.
    */
  def get(tagValue: String): List[IamRoleInfo] =
    Option(cache.getIfPresent(tagValue))
      .map(_.values.flatten.toList)
      .getOrElse(Nil)

  /** Gives all the roles in the cache. Useful for showing a cache status page.
    */
  def getAll: Map[String, List[IamRoleInfo]] = cache
    .asMap()
    .asScala
    .view
    .mapValues(_.values.flatten.toList)
    .toMap
}
