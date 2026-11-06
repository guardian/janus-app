package models

import play.api.libs.json.*

/** Utility for formatting Play JSON validation errors into human-readable
  * messages. Provides detailed error information including paths and context
  * hints.
  */
object JsonErrorFormatter {

  /** Formats a sequence of JSON validation errors into a comprehensive error
    * message.
    *
    * @param errors
    *   The validation errors from Play JSON
    * @param originalJson
    *   The original JSON string for context (optional)
    * @return
    *   A formatted error message with paths, details, and helpful hints
    */
  def formatJsErrors(
      errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])],
      originalJson: String
  ): String = {
    val errorDetails = errors
      .map { (path, errs) =>
        val pathStr = if (path == JsPath()) "root" else path.toString()
        val errorMessages = errs.map(formatValidationError).mkString("; ")
        val contextHint = getJsonContextHint(path, originalJson)

        s"  • Path: $pathStr\n    Error: $errorMessages$contextHint"
      }
      .mkString("\n")

    s"""JSON validation failed with ${errors.size} error(s):
    $errorDetails
    """
  }

  /** Generates a context hint for a specific JSON path to help with debugging.
    *
    * @param path
    *   The JSON path where the error occurred
    * @param jsonString
    *   The original JSON string
    * @return
    *   A context hint string, or empty string if no useful context can be
    *   provided
    */
  private def getJsonContextHint(path: JsPath, jsonString: String): String = {
    if (path == JsPath() || jsonString.isEmpty) return ""

    try {
      // Try to extract the problematic value for better context
      val pathNodes = path.path
      if (pathNodes.nonEmpty) {
        val firstNode = pathNodes.head match {
          case KeyPathNode(key) => Some(key)
          case _                => None
        }
        firstNode match {
          case Some(node) if jsonString.contains(s""""$node"""") =>
            s"\n    Context: Check the structure for node '$node'"
          case _ => ""
        }
      } else ""
    } catch {
      case _: Exception => ""
    }
  }

  /** Formats a single JsonValidationError into a readable message.
    *
    * @param error
    *   The validation error to format
    * @return
    *   A formatted error message string
    */
  private def formatValidationError(error: JsonValidationError): String = {
    val message = error.message
    val args = error.args

    message match {
      case msg if msg.startsWith("error.expected") =>
        s"Expected ${msg.replace("error.expected.", "").replace("js", "")}"
      case _ =>
        // For other messages, include the raw message and any arguments
        if (args.nonEmpty) {
          s"$message (${args.mkString(", ")})"
        } else message
    }
  }
}
