package com.gu.janus

import cats.Monoid
import com.gu.janus.model.{ACL, JanusData, Permission, Role, ValidationResult}

object Validation {

  def policySizeChecks(janusData: JanusData): ValidationResult = {
    // AWS doesn't reveal how this limit is being calculated
    // but based on trial and error it seems to be around 1050
    val sizeLimit = 1050

    val largePermissions = for {
      largePermission <- JanusConfig.allPermissions(janusData).filter { perm =>
        // session policy limit includes the managed ARNs and inline policy document
        val totalLength =
          perm.policy // the inline policy document's size
            .map(_.length)
            .getOrElse(0) +
            perm.managedPolicyArns // and the total size of the attached managed policy ARNs
              .map(_.map(_.length).sum)
              .getOrElse(0)
        totalLength >= sizeLimit
      }
    } yield s"${largePermission.label} (${largePermission.description})"

    if (largePermissions.isEmpty) {
      valid
    } else {
      warning {
        List(
          s"The following policies are likely to be too large ${largePermissions
              .mkString("`", ", ", "`")}"
        )
      }
    }
  }

  /** Permission labels need to be unique within each account so they can be
    * unambiguously looked up from the URL.
    */
  def permissionUniqueness(janusData: JanusData): ValidationResult = {
    val duplicates = JanusConfig.allPermissions(janusData)
      .groupBy(_.id)
      .filter { case (_, permissions) =>
        permissions.size > 1
      }

    val errors = duplicates.map { case (id, permissions) =>
      val account =
        permissions.headOption.map(_.account.name).getOrElse("Unknown account")
      val label = permissions.headOption.map(_.label).getOrElse("Unknown label")
      val names = permissions.map(_.description)
      s"Policy id $id is not unique, there are ${permissions.size} policies in account `$account` called `$label` (${names
          .mkString("`", ", ", "`")})"
    }.toList
    error(errors)
  }

  def valid: ValidationResult = ValidationResult(Nil, Nil)
  def error(errors: List[String]): ValidationResult =
    ValidationResult(errors, Nil)
  def warning(warnings: List[String]): ValidationResult =
    ValidationResult(Nil, warnings)

  def isClean(validationResult: ValidationResult): Boolean = {
    validationResult.errors.isEmpty && validationResult.warnings.isEmpty
  }
  def noErrors(validationResult: ValidationResult): Boolean = {
    validationResult.errors.isEmpty
  }

  implicit val validationResultMonoid: Monoid[ValidationResult] =
    new Monoid[ValidationResult] {
      def empty: ValidationResult = valid
      def combine(
          vr1: ValidationResult,
          vr2: ValidationResult
      ): ValidationResult = {
        ValidationResult(vr1.errors ++ vr2.errors, vr1.warnings ++ vr2.warnings)
      }
    }
}
