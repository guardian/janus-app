package com.gu.janus

import java.io.File

import com.gu.janus.JanusConfig.JanusConfigurationException

import scala.Console.{GREEN, RED, RESET}
import scala.util.control.NonFatal


object VerifyJanusData {
  def main(args: Array[String]): Unit = {
    args.headOption.fold {
      Console.err.println(
        s"""${RED}Error: Missing argument <output file>
           |
           |Usage: example <output file>$RESET""".stripMargin)
      System.exit(1)
    } { fileName =>
      try {
        val file = new File(fileName)
        if (file.exists()) {
          JanusConfig.load(file)
          println(
            s"""${GREEN}JanusData was loaded$RESET""".stripMargin)
        } else {
          Console.err.println(
            s"""$RED$fileName does not exist$RESET""".stripMargin)
          System.exit(1)
        }
      } catch {
        case e: JanusConfigurationException =>
          Console.err.println(
            s"""$RED${e.getMessage}$RESET""".stripMargin)
          System.exit(1)
        case NonFatal(e) =>
          Console.err.println(
            s"""${RED}Error loading JanusData: ${e.getMessage}>$RESET""".stripMargin)
          System.exit(1)
      }
    }
  }
}
