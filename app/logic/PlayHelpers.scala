package logic

object PlayHelpers {

  /** Play 2.5 used to extract multiple values from a single querystring
    * parameter. In Play 2.6+ you are expected to provide multiple parameters
    * with the same name.
    *
    * This function mimics Play 2.5 behaviour, which is what we want here.
    */
  def splitQuerystringParam(values: String): List[String] = {
    if (values.isEmpty) Nil
    else values.split(",").toList
  }
}
