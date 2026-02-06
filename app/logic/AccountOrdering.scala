package logic

import com.gu.janus.model.{AwsAccount, Permission}
import models.AwsAccountAccess

object AccountOrdering {

  /** Given the permissions available to the user and that user's 'favourites',
    * returns the sorted list of account access sets that can be displayed on
    * the site.
    *
    * Sorted first by the number of permissions for each account (to optimise
    * layout), then adds the favourites to the front in the user's order.
    */
  def orderedAccountAccess(
      permissions: Set[AwsAccountAccess],
      favourites: List[String] = Nil
  ): List[AwsAccountAccess] = {
    permissions
      .groupBy(_.awsAccount)
      .toList
      .sortBy { case (acct, _) => acct.name.toLowerCase }
      .sortBy { case (_, perms) => perms.size * -1 }
      .sortBy { case (awsAccount, _) =>
        val favIndex = favourites.indexOf(awsAccount.authConfigKey)
        if (favIndex < 0) favIndex + favourites.size + 1
        else favIndex
      }
      .flatMap(_._2)
  }

  /** 'dev' then then alphabetical 'others', finally 'cloudformation' (admin)
    * hard-coded dev/admin conventions for now, TODO: find a better
    * representation for ordering
    */
  given permissionOrdering: Ordering[Permission] =
    (p1: Permission, p2: Permission) => {
      def sortKey(p: Permission) = p.label match {
        case "dev"            => "aaaa"
        case "cloudformation" => "zzzz"
        case label            => label
      }

      sortKey(p1) compare sortKey(p2)
    }

  def isFavourite(
      awsAccount: AwsAccount,
      maybeFavs: Option[List[String]] = None
  ): Boolean = {
    maybeFavs match {
      case Some(favourites) =>
        favourites.contains(awsAccount.authConfigKey)
      case None =>
        false
    }
  }
}
