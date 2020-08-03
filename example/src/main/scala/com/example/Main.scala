package com.example

import java.io.PrintWriter

import com.gu.janus.JanusConfig
import com.gu.janus.model.JanusData

import scala.Console.{GREEN, RED, RESET, YELLOW}


object Main {

  val janusData = JanusData(
    accounts = Accounts.allAccounts,
    access = Access.acl,
    admin = Admin.acl,
    support = Support.acl
  )

  val outputFile = "exampleData.conf"

  def main(args: Array[String]): Unit = {
    val validationResult = JanusConfig.validate(janusData)
    if (validationResult.warnings.nonEmpty) {
      Console.err.println(
        s"""
           |${YELLOW}The following validation warnings were generated from the Janus Configuration:
           |\n- ${validationResult.warnings.mkString("\n- ")}$RESET""".stripMargin)
    }
    if (validationResult.errors.nonEmpty) {
      Console.err.println(
        s"""
           |${RED}The following validation warnings were generated from the Janus Configuration:
           |\n- ${validationResult.errors.mkString("\n- ")}$RESET""".stripMargin)
      System.exit(1)
    } else {
      val content = JanusConfig.write(janusData)
      Console.out.println(
        s"""
           |${GREEN}Successfully generated Janus Data with filename: $outputFile
           |\n${YELLOW}*** Do not commit $outputFile to version control ***\n$RESET""".stripMargin)
      new PrintWriter(outputFile) {
        try {
          write(content)
        } finally {
          close()
        }
      }
    }
  }
}
