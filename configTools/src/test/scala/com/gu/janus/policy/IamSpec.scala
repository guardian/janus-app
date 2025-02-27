package com.gu.janus.policy

import com.gu.janus.policy.Iam._
import io.circe.JsonObject
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class IamSpec extends AnyFreeSpec with Matchers {

  "Action" - {
    "should encode action name correctly" in {
      Action("s3:GetObject").asJson.noSpaces shouldBe """"s3:GetObject""""
    }
  }

  "Resource" - {
    "should encode resource id correctly" in {
      Resource(
        "arn:aws:s3:::my-bucket/*"
      ).asJson.noSpaces shouldBe """"arn:aws:s3:::my-bucket/*""""
    }
  }

  "Statement" - {
    val basicStatement = Statement(
      effect = Effect.Allow,
      actions = List(Action("iam:PutRolePolicy"), Action("iam:getRole")),
      resources = List(Resource("*")),
      id = None
    )

    "should encode basic statement correctly" in {
      val expected = """{
                         |"Effect":"Allow",
                         |"Action":["iam:PutRolePolicy","iam:getRole"],
                         |"Resource":["*"]
                         |}""".stripMargin.replaceAll("\\s", "")

      basicStatement.asJson.noSpaces shouldBe expected
    }

    "should encode statement with ID correctly" in {
      val statementWithId = basicStatement.copy(id = Some("statement1"))
      val expected = """{
                         |"Effect":"Allow",
                         |"Action":["iam:PutRolePolicy","iam:getRole"],
                         |"Resource":["*"],
                         |"Sid":"statement1"
                         |}""".stripMargin.replaceAll("\\s", "")

      statementWithId.asJson.noSpaces shouldBe expected
    }

    "should encode statement with single condition correctly" in {
      val statementWithCondition = basicStatement.copy(
        conditions = List(
          Condition("aws:SourceIp", "StringEquals", List("203.0.113.0/24"))
        )
      )

      val expected = """{
                       |  "Effect" : "Allow",
                       |  "Action" : [
                       |    "iam:PutRolePolicy",
                       |    "iam:getRole"
                       |  ],
                       |  "Resource" : [
                       |    "*"
                       |  ],
                       |  "Condition" : {
                       |    "StringEquals" : {
                       |      "aws:SourceIp" : [
                       |        "203.0.113.0/24"
                       |      ]
                       |    }
                       |  }
                       |}""".stripMargin

      statementWithCondition.asJson.spaces2 shouldBe expected
    }

    "should encode statement with single condition with multiple values correctly" in {
      val statementWithCondition = basicStatement.copy(
        conditions = List(
          Condition(
            "aws:SourceIp",
            "StringEquals",
            List("203.0.113.0/24", "203.0.113.1/24")
          )
        )
      )

      val expected = """{
                       |  "Effect" : "Allow",
                       |  "Action" : [
                       |    "iam:PutRolePolicy",
                       |    "iam:getRole"
                       |  ],
                       |  "Resource" : [
                       |    "*"
                       |  ],
                       |  "Condition" : {
                       |    "StringEquals" : {
                       |      "aws:SourceIp" : [
                       |        "203.0.113.0/24",
                       |        "203.0.113.1/24"
                       |      ]
                       |    }
                       |  }
                       |}""".stripMargin

      statementWithCondition.asJson.spaces2 shouldBe expected
    }

    "should encode statement with multiple conditions correctly" in {
      val statementWithConditions = basicStatement.copy(
        conditions = List(
          Condition("aws:SourceIp", "StringEquals", List("203.0.113.0/24")),
          Condition(
            "aws:TokenIssueTime",
            "DateLessThan",
            List("2025-02-27T09:33:01.000Z")
          )
        )
      )

      val expected = """{
                       |  "Effect" : "Allow",
                       |  "Action" : [
                       |    "iam:PutRolePolicy",
                       |    "iam:getRole"
                       |  ],
                       |  "Resource" : [
                       |    "*"
                       |  ],
                       |  "Condition" : {
                       |    "StringEquals" : {
                       |      "aws:SourceIp" : [
                       |        "203.0.113.0/24"
                       |      ]
                       |    },
                       |    "DateLessThan" : {
                       |      "aws:TokenIssueTime" : [
                       |        "2025-02-27T09:33:01.000Z"
                       |      ]
                       |    }
                       |  }
                       |}""".stripMargin

      statementWithConditions.asJson.spaces2 shouldBe expected
    }

    "should encode statement with single principal correctly" in {
      val statementWithPrincipal = basicStatement.copy(principals =
        List(Principal("AROAXXXXXXXXXXXXXXX1", "AWS"))
      )

      val expected = """{
                       |  "Effect" : "Allow",
                       |  "Action" : [
                       |    "iam:PutRolePolicy",
                       |    "iam:getRole"
                       |  ],
                       |  "Resource" : [
                       |    "*"
                       |  ],
                       |  "Principal" : {
                       |    "AWS" : [
                       |      "AROAXXXXXXXXXXXXXXX1"
                       |    ]
                       |  }
                       |}""".stripMargin

      statementWithPrincipal.asJson.spaces2 shouldBe expected
    }

    "should encode statement with multiple principals correctly" in {
      val statementWithPrincipals = basicStatement.copy(
        principals = List(
          Principal("AROAXXXXXXXXXXXXXXX1", "AWS"),
          Principal("AROAXXXXXXXXXXXXXXX2", "AWS"),
          Principal("ServiceAccount1", "Kubernetes")
        )
      )

      val expected = """{
                         |"Effect":"Allow",
                         |"Action":["iam:PutRolePolicy","iam:getRole"],
                         |"Resource":["*"],
                         |"Principal":{
                         |"AWS":["AROAXXXXXXXXXXXXXXX1","AROAXXXXXXXXXXXXXXX2"],
                         |"Kubernetes":["ServiceAccount1"]
                         |}
                         |}""".stripMargin.replaceAll("\\s", "")

      statementWithPrincipals.asJson.noSpaces shouldBe expected
    }
  }

  "Policy" - {
    val basicStatement = Statement(
      effect = Effect.Allow,
      actions = List(Action("s3:GetObject")),
      resources = List(Resource("arn:aws:s3:::my-bucket/*"))
    )

    "should encode basic policy correctly" in {
      val policy = Policy(statements = List(basicStatement))
      val expected = """{
                         |"Version":"2012-10-17",
                         |"Statement":[{
                         |"Effect":"Allow",
                         |"Action":["s3:GetObject"],
                         |"Resource":["arn:aws:s3:::my-bucket/*"]
                         |}]
                         |}""".stripMargin.replaceAll("\\s", "")

      policy.asJson.noSpaces shouldBe expected
    }

    "should encode policy with ID correctly" in {
      val policy = Policy(
        statements = List(basicStatement),
        id = Some("MyPolicyId")
      )

      val expected = """{
                         |"Version":"2012-10-17",
                         |"Statement":[{
                         |"Effect":"Allow",
                         |"Action":["s3:GetObject"],
                         |"Resource":["arn:aws:s3:::my-bucket/*"]
                         |}],
                         |"Id":"MyPolicyId"
                         |}""".stripMargin.replaceAll("\\s", "")

      policy.asJson.noSpaces shouldBe expected
    }

    "should encode complex policy correctly" - {
      val complexPolicy = Policy(
        statements = List(
          Statement(
            effect = Effect.Allow,
            actions = List(Action("s3:GetObject"), Action("s3:PutObject")),
            resources = List(
              Resource("arn:aws:s3:::my-bucket/*"),
              Resource("arn:aws:s3:::my-bucket-2/*")
            ),
            id = Some("statement1"),
            conditions = List(
              Condition("aws:SourceIp", "StringEquals", List("203.0.113.0/24"))
            ),
            principals = List(
              Principal("AROAXXXXXXXXXXXXXXX", "AWS"),
              Principal("ServiceAccount1", "Kubernetes")
            )
          )
        ),
        id = Some("ComplexPolicy")
      )

      val json = complexPolicy.asJson.noSpaces
      decode[JsonObject](json).isRight shouldBe true

      "with correct version" in {
        val json = complexPolicy.asJson.noSpaces
        json should include(""""Version":"2012-10-17"""")
      }

      "with correct ID" in {
        val json = complexPolicy.asJson.noSpaces
        json should include(""""Id":"ComplexPolicy"""")
      }

      "with correct effect" in {
        val json = complexPolicy.asJson.noSpaces
        json should include(""""Effect":"Allow"""")
      }

      "with explicitly set statement ID" in {
        val json = complexPolicy.asJson.noSpaces
        json should include(""""Sid":"statement1"""")
      }

      "with correct condition" in {
        val json = complexPolicy.asJson.noSpaces
        json should include(""""aws:SourceIp":["203.0.113.0/24"]""")
      }

      "with correct principal" in {
        val json = complexPolicy.asJson.noSpaces
        json should include(""""AWS":["AROAXXXXXXXXXXXXXXX"]""")
      }

      "with correct second principal" in {
        val json = complexPolicy.asJson.noSpaces
        json should include(""""Kubernetes":["ServiceAccount1"]""")
      }

      "with correct complete json string" in {
        val json = complexPolicy.asJson.spaces2
        val expected = """{
                         |  "Version" : "2012-10-17",
                         |  "Statement" : [
                         |    {
                         |      "Effect" : "Allow",
                         |      "Action" : [
                         |        "s3:GetObject",
                         |        "s3:PutObject"
                         |      ],
                         |      "Resource" : [
                         |        "arn:aws:s3:::my-bucket/*",
                         |        "arn:aws:s3:::my-bucket-2/*"
                         |      ],
                         |      "Sid" : "statement1",
                         |      "Condition" : {
                         |        "StringEquals" : {
                         |          "aws:SourceIp" : [
                         |            "203.0.113.0/24"
                         |          ]
                         |        }
                         |      },
                         |      "Principal" : {
                         |        "AWS" : [
                         |          "AROAXXXXXXXXXXXXXXX"
                         |        ],
                         |        "Kubernetes" : [
                         |          "ServiceAccount1"
                         |        ]
                         |      }
                         |    }
                         |  ],
                         |  "Id" : "ComplexPolicy"
                         |}""".stripMargin
        json shouldBe expected
      }
    }
  }
}
