package logic

import com.gu.janus.model.{AwsAccount, Permission}
import models.{AccountAccess, AwsAccountAccess, DeveloperPolicy}

object AccountOrdering {

  /** Given the per-account access available to the user and that user's
    * 'favourites', returns the sorted list of [[AwsAccountAccess]] that can be
    * displayed on the site.
    *
    * Sorted first by the number of permissions for each account (to optimise
    * layout), then adds the favourites to the front in the user's order.
    */
  def orderedAccountAccess(
      accountAccess: Map[AwsAccount, AccountAccess],
      favourites: List[String] = Nil
  ): List[AwsAccountAccess] =
    accountAccess.toList
      .sortBy { case (account, _) => account.name.toLowerCase }
      .sortBy { case (_, access) => access.permissions.size * -1 }
      .sortBy { case (account, _) =>
        val favIndex = favourites.indexOf(account.authConfigKey)
        if (favIndex < 0) favIndex + favourites.size + 1
        else favIndex
      }
      .map { case (account, access) =>
        AwsAccountAccess(
          awsAccount = account,
          access = AccountAccess(
            permissions = access.permissions.sorted,
            developerPolicies = access.developerPolicies.sorted
          ),
          isFavourite = favourites.contains(account.authConfigKey)
        )
      }

  /** 'dev' then then alphabetical 'others', finally 'cloudformation' (admin)
    * hard-coded dev/admin conventions for now, TODO: find a better
    * representation for ordering
    */
  given Ordering[Permission] =
    (p1: Permission, p2: Permission) => {
      def sortKey(p: Permission) = p.label match {
        case "dev"            => "aaaa"
        case "cloudformation" => "zzzz"
        case label            => label
      }

      sortKey(p1) compare sortKey(p2)
    }

  given Ordering[DeveloperPolicy] =
    Ordering.by(policy => s"${policy.policyGrantId}:${policy.policyArn}")
}
