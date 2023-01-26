package com.example

import java.io.PrintWriter

import com.gu.janus.JanusConfig

import scala.Console.{GREEN, RED, RESET, YELLOW}

object Main {

  def main(args: Array[String]): Unit = {
    args.toList match {
      case outputFile :: _ =>
        val validationResult = JanusConfig.validate(Data.janusData)
        if (validationResult.warnings.nonEmpty) {
          Console.err.println(s"""
               |${YELLOW}The following validation warnings were generated from the Janus Configuration:
               |\n- ${validationResult.warnings.mkString(
                                  "\n- "
                                )}$RESET""".stripMargin)
        }
        if (validationResult.errors.nonEmpty) {
          Console.err.println(s"""
               |${RED}The following validation warnings were generated from the Janus Configuration:
               |\n- ${validationResult.errors.mkString(
                                  "\n- "
                                )}$RESET""".stripMargin)
          System.exit(1)
        } else {
          val content = JanusConfig.write(Data.janusData)
          Console.out.println(
            s"""
               |${GREEN}Successfully generated Janus Data with filename: $outputFile
               |\n${YELLOW}*** Do not commit $outputFile to version control ***\n$RESET""".stripMargin
          )
          new PrintWriter(outputFile) {
            try {
              write(content)
            } finally {
              close()
            }
          }
        }
      case Nil =>
        Console.err.println(s"""
             |${RED}Error: Missing argument <output file>
             |\nUsage: example <output file>$RESET""".stripMargin)
        System.exit(1)
    }
  }
}
