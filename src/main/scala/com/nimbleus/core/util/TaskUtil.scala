package com.nimbleus.core.util

import java.util.concurrent.{TimeUnit, TimeoutException}
import org.jboss.netty.util.{Timeout, TimerTask, HashedWheelTimer}
import scala.collection.generic.CanBuildFrom
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Success, Try}

object TaskUtil {
  /**
   * This function executes a partial function and wraps the result
   * in a Try. If the partial function fails then the number of retries
   * id decremented and the retry function is re-executed via recursion
   * until the attempt number reaches 0.
   *
   * @param n the current attempt number
   * @param fn the partial function to execute
   * @tparam T the result type.
   * @return a Try wrapping an error of the result type.
   */
  @annotation.tailrec
  final def retry[T](n: Int)(fn: => T): Try[T] = {
    Try { fn } match {
      case x: Success[T] => x
      case _ if n > 1 => {
        retry(n - 1)(fn)
      }
      case f => f
    }
  }

  /**
   * This function allows a future to "timeout" after a specific duration.
   *
   * @param fut the future to monitor
   * @param ec the execution context
   * @param after the duration after which to timeout
   * @tparam T the result type of the future
   * @return the first completed future, the promise or the timeout
   */
  def withTimeout[T](fut:Future[T])(implicit ec:ExecutionContext, after:Duration) = {
    val prom = Promise[T]()
    val timeout = TimeoutScheduler.scheduleTimeout(prom, after)
    val combinedFut = Future.firstCompletedOf(List(fut, prom.future))
    fut onComplete{case result => timeout.cancel()}
    combinedFut
  }

  //
  /**
   * This function executes over the given collection of futures and returns a future
   * that is comprised of that same list type of types.
   *
   * @param collection the collection to iterate
   * @param fn the partial function to execute
   * @param ec the execution context to run within
   * @param cbf the future builder
   * @tparam A the type of instances in the collection
   * @tparam B the result type
   * @tparam C the type of return collection
   * @return  a collection of completed futures
   */
  def serialiseFutures[A, B, C[A] <: Iterable[A]]
  (collection: C[A])(fn: A ⇒ Future[B])(
    implicit ec: ExecutionContext,
    cbf: CanBuildFrom[C[B], B, C[B]]): Future[C[B]] = {
    val builder = cbf()
    builder.sizeHint(collection.size)

    collection.foldLeft(Future(builder)) {
      (previousFuture, next) ⇒
        for {
          previousResults ← previousFuture
          next ← fn(next)
        } yield previousResults += next
    } map { builder ⇒ builder.result }
  }
}

/**
 * This object exposed a scheduled timer that will complete a timeout promise.
 */
object TimeoutScheduler{
  val timer = new HashedWheelTimer(10, TimeUnit.MILLISECONDS)
  def scheduleTimeout(promise:Promise[_], after:Duration) = {
    timer.newTimeout(new TimerTask{
      def run(timeout:Timeout){
        promise.failure(new TimeoutException("Operation timed out after " + after.toMillis + " millis"))
      }
    }, after.toNanos, TimeUnit.NANOSECONDS)
  }
}
