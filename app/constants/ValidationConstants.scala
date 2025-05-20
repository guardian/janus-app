package constants

/**
 * Shared validation constants used across the application
 * These constants are used by both server-side code and exposed to client-side JavaScript
 */
object ValidationConstants {
  // Passkey validation constants
  object PasskeyName {
    val REGEX_PATTERN = "^[a-zA-Z0-9 _-]*$"
    val MAX_LENGTH = 50
  }
  
  /**
   * Convert constants to JavaScript
   * This creates a JavaScript object that can be included in the page
   */
  def toJavaScript: String = 
    s"""window.ValidationConstants = {
       |  PASSKEY_NAME: {
       |    REGEX_PATTERN: "${PasskeyName.REGEX_PATTERN}",
       |    MAX_LENGTH: ${PasskeyName.MAX_LENGTH}
       |  }
       |};
       |""".stripMargin
}
