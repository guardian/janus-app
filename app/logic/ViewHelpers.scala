package logic

import com.gu.janus.model.AwsAccount


object ViewHelpers {
  // created as Scala function to make it easier to control whitespace
  def shellCredentials(accountsCredentials: List[(AwsAccount, awscala.sts.TemporaryCredentials)]): String = {
    (for {
      (account, credentials) <- accountsCredentials
    } yield {
      s""" aws configure set aws_access_key_id ${credentials.accessKeyId} --profile ${account.authConfigKey} && \\
         | aws configure set aws_secret_access_key ${credentials.secretAccessKey} --profile ${account.authConfigKey} && \\
         | aws configure set aws_session_token ${credentials.sessionToken} --profile ${account.authConfigKey}""".stripMargin
    }).mkString(start = "", sep = " && \\\n", end = "\n")
  }
}
