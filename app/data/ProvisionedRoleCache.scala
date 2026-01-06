package data

import models.IamRoleInfo

import java.util.concurrent.atomic.AtomicReference

class ProvisionedRoleCache {
  private val cache =
    new AtomicReference[Map[String, List[IamRoleInfo]]](Map.empty)

  def findByTag(tagValue: String): List[IamRoleInfo] =
    cache.get().getOrElse(tagValue, List.empty)

  def update(roles: Map[String, List[IamRoleInfo]]): Unit =
    cache.set(roles)

  def getAll: Map[String, List[IamRoleInfo]] = cache.get()
}
