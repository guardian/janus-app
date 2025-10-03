package filters

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.playpasskeyauth.web.RequestWithAuthenticationData
import play.api.mvc.{ActionFilter, ActionRefiner, AnyContent, Request, Result}

import scala.concurrent.{ExecutionContext, Future}

/** A conditional action refiner that transforms request types based on a predicate.
  *
  * This uses ActionRefiner to properly transform UserIdentityRequest to RequestWithAuthenticationData
  * when the predicate is true, or passes through unchanged when false.
  */
class ConditionalPasskeyRefiner(
    hasPasskeyPredicate: UserIdentityRequest[_] => Future[Boolean],
    requestEnricher: UserIdentityRequest[_] => RequestWithAuthenticationData[_]
)(using ec: ExecutionContext) extends ActionRefiner[UserIdentityRequest, Request] {

  def executionContext: ExecutionContext = ec

  def refine[A](request: UserIdentityRequest[A]): Future[Either[Result, Request[A]]] = {
    hasPasskeyPredicate(request).map { hasPasskey =>
      if (hasPasskey) {
        // Transform to RequestWithAuthenticationData
        val enrichedRequest = requestEnricher(request).asInstanceOf[Request[A]]
        Right(enrichedRequest)
      } else {
        // Pass through original request unchanged
        Right(request)
      }
    }
  }
}

/** A passkey authentication filter that only applies when the request is RequestWithAuthenticationData */
class ConditionalPasskeyAuthFilter(
    passkeyVerificationLogic: RequestWithAuthenticationData[_] => Future[Option[Result]]
)(using ec: ExecutionContext) extends ActionFilter[Request] {

  def executionContext: ExecutionContext = ec

  def filter[A](request: Request[A]): Future[Option[Result]] = {
    // Use runtime type checking instead of pattern matching to avoid type erasure issues
    if (request.getClass.getName.contains("RequestWithAuthenticationData")) {
      // Safe to cast since we checked the class name
      val authRequest = request.asInstanceOf[RequestWithAuthenticationData[A]]
      passkeyVerificationLogic(authRequest)
    } else {
      // Pass through other request types unchanged
      Future.successful(None)
    }
  }
}

/** Generic conditional filter using ActionRefiner pattern */
class GenericConditionalRefiner[From[_] <: Request[_], To[_] <: Request[_]](
    predicate: From[Any] => Future[Boolean],
    transformer: From[Any] => To[Any]
)(using ec: ExecutionContext) extends ActionRefiner[From, To] {

  def executionContext: ExecutionContext = ec

  def refine[A](request: From[A]): Future[Either[Result, To[A]]] = {
    predicate(request.asInstanceOf[From[Any]]).map { shouldTransform =>
      if (shouldTransform) {
        Right(transformer(request.asInstanceOf[From[Any]]).asInstanceOf[To[A]])
      } else {
        // For this generic version, we can't easily pass through unchanged
        // since From and To might be different types
        Right(request.asInstanceOf[To[A]]) // This requires careful use
      }
    }
  }
}

object ConditionalAction {

  /** Creates a passkey conditional refiner + auth filter combination
    *
    * Returns a tuple of (refiner, authFilter) that can be chained with andThen
    */
  def forPasskeys(
      hasPasskeyPredicate: UserIdentityRequest[_] => Future[Boolean],
      passkeyVerificationLogic: RequestWithAuthenticationData[_] => Future[Option[Result]],
      requestEnricher: UserIdentityRequest[_] => RequestWithAuthenticationData[_]
  )(using ExecutionContext): (ConditionalPasskeyRefiner, ConditionalPasskeyAuthFilter) = {
    val refiner = new ConditionalPasskeyRefiner(hasPasskeyPredicate, requestEnricher)
    val authFilter = new ConditionalPasskeyAuthFilter(passkeyVerificationLogic)
    (refiner, authFilter)
  }

  /** Creates a single combined filter for simpler usage */
  def passkeyFilter(
      hasPasskeyPredicate: UserIdentityRequest[_] => Future[Boolean],
      passkeyVerificationLogic: RequestWithAuthenticationData[_] => Future[Option[Result]],
      requestEnricher: UserIdentityRequest[_] => RequestWithAuthenticationData[_]
  )(using ExecutionContext): ActionFilter[UserIdentityRequest] = {
    new ActionFilter[UserIdentityRequest] {
      def executionContext: ExecutionContext = summon[ExecutionContext]

      def filter[A](request: UserIdentityRequest[A]): Future[Option[Result]] = {
        hasPasskeyPredicate(request).flatMap { hasPasskey =>
          if (hasPasskey) {
            val enrichedRequest = requestEnricher(request)
            passkeyVerificationLogic(enrichedRequest)
          } else {
            Future.successful(None)
          }
        }
      }
    }
  }

  /** Generic conditional logic for any request type */
  def conditional[R[_] <: Request[_]](
      predicate: R[Any] => Future[Boolean],
      whenTrueLogic: R[Any] => Future[Option[Result]],
      whenFalseLogic: R[Any] => Future[Option[Result]] = (request: R[Any]) => Future.successful(None)
  )(using ExecutionContext): ActionFilter[R] = {
    new ActionFilter[R] {
      def executionContext: ExecutionContext = summon[ExecutionContext]

      def filter[A](request: R[A]): Future[Option[Result]] = {
        predicate(request.asInstanceOf[R[Any]]).flatMap { predicateResult =>
          if (predicateResult) {
            whenTrueLogic(request.asInstanceOf[R[Any]])
          } else {
            whenFalseLogic(request.asInstanceOf[R[Any]])
          }
        }
      }
    }
  }
}
