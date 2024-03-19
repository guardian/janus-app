package com.gu.janus.policy

import io.circe.Json
import io.circe.parser.parse


object PolicyJson {
  def stripSids(rawPolicyJson: String): String = {
    val result = for {
      json <- parse(rawPolicyJson)
      updatedCursor = json.hcursor.downField("Statement").withFocus { rawStatementsJson =>
        rawStatementsJson.asArray.map { statementsJson =>
          Json.fromValues(statementsJson.map { statementJson =>
            statementJson.hcursor.downField("Sid").delete.top
              // if we can't remove a Sid, return the original statement
              .getOrElse(statementJson)
          })
        }.getOrElse(rawStatementsJson)
      }
    } yield updatedCursor.top.map(_.noSpaces).getOrElse(rawPolicyJson)
    result.toTry.get
  }
}
