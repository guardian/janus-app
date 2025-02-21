package com.gu.janus.testutils

import org.scalactic.source.Position
import org.scalatest.exceptions.{StackDepthException, TestFailedException}

trait RightValues:
  extension [L, R](e: Either[L, R])
    def rightValue(using pos: Position): R = e match
      case Right(r) => r
      case Left(l)  => throw TestFailedException(
          (_: StackDepthException) =>
            Some(s"The Either on which value was invoked was not a Right, got Left($l)"),
          None,
          pos
        )

object RightValues extends RightValues
