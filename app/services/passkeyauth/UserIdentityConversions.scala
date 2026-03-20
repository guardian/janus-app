package services.passkeyauth

import com.gu.googleauth.UserIdentity
import com.gu.playpasskeyauth.models.UserId

object UserIdentityConversions {
  def toUserIdentity(userId: UserId): UserIdentity =
    UserIdentity(
      sub = s"passkey-${userId.value}",
      email = s"${userId.value}@example.com",
      firstName = userId.value,
      lastName = "",
      exp = Long.MaxValue,
      avatarUrl = None
    )
}
