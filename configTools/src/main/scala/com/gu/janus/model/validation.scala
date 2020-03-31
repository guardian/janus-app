package com.gu.janus.model

case class ValidationResult(errors: List[String], warnings: List[String]) {
  val valid: Boolean = errors.isEmpty && warnings.isEmpty
}
