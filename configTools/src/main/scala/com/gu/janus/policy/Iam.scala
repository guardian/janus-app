package com.gu.janus.policy

import io.circe.syntax._
import io.circe.{Encoder, Json, JsonObject}

import scala.collection.immutable.ListMap
import scala.collection.mutable

/** This models the AWS IAM policy types and subtypes.
  *
  * See https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies.html
  * for the logic of the Json encoding.
  */
object Iam {

  sealed trait Effect
  object Effect {
    case object Allow extends Effect
    case object Deny extends Effect

    implicit val encoder: Encoder[Effect] = Encoder.instance {
      case Allow => Json.fromString("Allow")
      case Deny  => Json.fromString("Deny")
    }
  }

  case class Action(name: String)
  object Action {
    implicit val encoder: Encoder[Action] = Encoder[String].contramap(_.name)
  }

  case class Resource(id: String)
  object Resource {
    implicit val encoder: Encoder[Resource] = Encoder[String].contramap(_.id)
  }

  case class Condition(
      key: String,
      typeName: String,
      conditionValues: Seq[String]
  )
  object Condition {
    implicit val encoder: Encoder[Condition] = Encoder.instance { condition =>
      Json.obj(
        condition.typeName -> Json.obj(
          condition.key -> Json.fromValues(
            condition.conditionValues.map(Json.fromString)
          )
        )
      )
    }
  }

  case class Principal(id: String, provider: String)
  object Principal {
    implicit val encoder: Encoder[Principal] = Encoder.instance { principal =>
      Json.obj(
        principal.provider -> Json.fromString(principal.id)
      )
    }
  }

  case class Statement(
      effect: Effect,
      actions: Seq[Action],
      resources: Seq[Resource],
      id: Option[String] = None,
      conditions: Seq[Condition] = Nil,
      principals: Seq[Principal] = Nil
  )
  object Statement {
    private def conditionsAsJson(conditions: Seq[Condition]): Json = {
      val mergedConditions = conditions
        .groupBy(_.typeName)
        .view
        .mapValues(conditions =>
          Json.obj(
            conditions.map(condition =>
              condition.key -> Json.fromValues(
                condition.conditionValues.map(Json.fromString)
              )
            ): _*
          )
        )
        .toMap
      Json.fromJsonObject(JsonObject.fromMap(mergedConditions))
    }

    private def principalsAsJson(principals: Seq[Principal]): Json = {
      val principalMap = principals
        .groupBy(_.provider)
        .view
        .mapValues(principals =>
          Json.fromValues(
            principals.map(p => Json.fromString(p.id))
          )
        )
        .toMap
      Json.fromJsonObject(JsonObject.fromMap(principalMap))
    }

    implicit val encoder: Encoder[Statement] = Encoder.instance { statement =>
      // Using ListMap to preserve order of fields, which is easier to debug
      val baseFields = ListMap(
        "Effect" -> statement.effect.asJson,
        "Action" -> statement.actions.asJson,
        "Resource" -> statement.resources.asJson
      )

      val withSid = statement.id.fold(baseFields)(id =>
        baseFields + ("Sid" -> Json.fromString(id))
      )

      val withConditions =
        if (statement.conditions.nonEmpty)
          withSid + ("Condition" -> conditionsAsJson(statement.conditions))
        else withSid

      val withPrincipals =
        if (statement.principals.nonEmpty)
          withConditions + ("Principal" -> principalsAsJson(
            statement.principals
          ))
        else withConditions

      Json.fromJsonObject(JsonObject.fromMap(withPrincipals))
    }
  }

  case class Policy(
      statements: Seq[Statement],
      id: Option[String] = None
  )

  object Policy {
    private val PolicyVersion = "2012-10-17"

    implicit val encoder: Encoder[Policy] = Encoder.instance { policy =>
      val baseObj = JsonObject(
        "Version" -> Json.fromString(PolicyVersion),
        "Statement" -> policy.statements.asJson
      )
      Json.fromJsonObject(
        policy.id.fold(baseObj)(id => baseObj.add("Id", Json.fromString(id)))
      )
    }
  }
}
