package com.gu.janus.testutils

import org.scalactic.source.Position
import org.scalatest.exceptions.{StackDepthException, TestFailedException}

trait RightValues {
  implicit class RichEither[L, R](e: Either[L, R]) {
    def value(implicit pos: Position): R = {
      e.fold(
        { l =>
          throw new TestFailedException(
            (_: StackDepthException) => Some {
              s"The Either on which value was invoked was not a Right, got Left($l)"
            },
            None,
            pos
          )
        },
        identity
      )
    }
  }
}
object RightValues extends RightValues
