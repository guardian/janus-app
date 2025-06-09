package controllers

import play.api.data.Form

private[controllers] object Validation {

  def formattedErrors[A](formWithErrors: Form[A]): String =
    formWithErrors.errors
      .map { error =>
        val message = error.message match {
          case "error.required"  => "missing value"
          case "error.maxLength" => "too long"
          case "error.pattern"   => "contains invalid characters"
          case other             => other
        }
        s"${error.key}: $message"
      }
      .mkString(", ")
}
