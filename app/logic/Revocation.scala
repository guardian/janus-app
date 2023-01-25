package logic

import com.gu.janus.model.AwsAccount

object Revocation {
  def checkConfirmation(
      confirmationCode: String,
      account: AwsAccount
  ): Boolean = {
    val normalised = confirmationCode.toLowerCase()
    account.name.toLowerCase == normalised || account.authConfigKey.toLowerCase == normalised
  }
}
