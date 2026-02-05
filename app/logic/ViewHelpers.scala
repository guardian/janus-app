package logic

import com.gu.janus.model.AwsAccount
import software.amazon.awssdk.services.sts.model.Credentials
import software.amazon.awssdk.arns.Arn
import scala.jdk.OptionConverters.RichOptional

object ViewHelpers {
  // created as Scala function to make it easier to control whitespace
  def shellCredentials(
      accountsCredentials: List[(AwsAccount, Credentials)]
  ): String = {
    (for {
      (account, credentials) <- accountsCredentials
    } yield {
      s""" aws configure set aws_access_key_id ${credentials.accessKeyId} --profile ${account.authConfigKey} && \\
         | aws configure set aws_secret_access_key ${credentials.secretAccessKey} --profile ${account.authConfigKey} && \\
         | aws configure set aws_session_token ${credentials.sessionToken} --profile ${account.authConfigKey}""".stripMargin
    }).mkString(start = "", sep = " && \\\n", end = "\n")
  }

  // Scala function so we can test it properly
  private[logic] def columnify[A](
      columnCount: Int,
      as: List[A]
  ): List[List[A]] = {
    val emptyAcc: Map[Int, List[A]] =
      (0.until(columnCount)).map(i => i -> Nil).toMap
    as.zipWithIndex
      .foldRight[Map[Int, List[A]]](emptyAcc) { case ((a, i), acc) =>
        val column = i % columnCount
        acc.updated(column, a :: acc(column))
      }
      .toList
      .sortBy(_._1)
      .map(_._2)
  }

  def getColumn[A](columnCount: Int, as: List[A], column: Int): List[A] = {
    assert(columnCount > 0, "column count must be at least 1")
    assert(
      column >= 0,
      "columns are 0 indexed, so column selection must be at least 0"
    )
    assert(
      column < columnCount,
      "column selection needs to be within the number of columns"
    )

    columnify(columnCount, as)
      .lift(column)
      .getOrElse(Nil)
  }
}
