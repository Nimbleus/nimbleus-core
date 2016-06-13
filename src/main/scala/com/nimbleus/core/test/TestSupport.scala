/* Copyright ©2014 Nimbleus LLC. <http://www.nimbleus.com>
 *
 * Nimbleus® are trademarks of Nimbleus LLC.  All other product
 * names, trade names, trademarks, and logos used in this documentation are the property of their
 * respective owners.  Use of any other company’s trademarks, trade names, product names and logos does
 * not constitute: (1) an endorsement by such other company of Nimbleus LLC. or its products or
 * services, or (2) an endorsement of by Nimbleus LLC. or such other company or its products or
 * services.
 *
 * This software includes code written by third parties, including Scala (Copyright ©2002-2013, EPFL,
 * Lausanne) and other code. Additional details regarding such third party code, including applicable
 * copyright, legal and licensing notices are available at:
 * http://support.nimbleus.com/thirdpartycode.
 *
 * No part of this documentation may be reproduced, transmitted, or otherwise distributed in any form or
 * by any means (electronic or otherwise) without the prior written consent of Nimbleus LLC.
 * You may not use this documentation for any purpose except in connection with your properly licensed
 * use or evaluation of Nimbleus LLC. software.  Any other use, including but not limited to
 * reverse engineering such software or creating derivative works thereof, is prohibited.  If your
 * license to access and use the software that this documentation accompanies is terminated, you must
 * immediately return this documentation to Nimbleus LLC. and destroy all copies you may have.
 *
 * IN NO EVENT SHALL NIMBLEUS LLC. BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT, SPECIAL,
 * INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING BUT NOT LIMITED TO LOST PROFITS, ARISING OUT OF THE
 * USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF NIMBLEUS LLC. HAS BEEN ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * NIMBLEUS LLC. SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE AND
 * ACCOMPANYING DOCUMENTATION, IF ANY, PROVIDED HEREUNDER IS PROVIDED "AS IS". NIMBLEUS LLC.
 * HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 */
package com.nimbleus.core.test

import java.util.concurrent.{Semaphore, TimeUnit, TimeoutException}

import akka.actor.{Actor, ActorSystem, PoisonPill, Props}

import scala.concurrent.duration._

class TestTimeoutException(description: String, elapsed: Double, nestedException: Throwable) extends Exception(
   s"Timed out after ${elapsed} seconds while waiting until ${description}", nestedException) {

   override def getMessage = getCause.getMessage
 }

/**
  * Provides utilities for unit and integration tests.
  */
trait TestSupport {

   /**
    * Waits until a given condition becomes true for a given duration.
    * @param description description of what is being watied for. This is used in the message on a failed case
    * @param timeout duration to await for the condition to become true
    * @param func function that takes a void and returns a null.
    *
    * Example usage:
    *
    *   waitUntil("the sun rises", 10 minutes) {
    *     hasTheSunRisen()
    *   }
    *
    */
   def waitUntil(description: String, timeout: Duration, pause: Duration = 1 second)(func: ⇒ Boolean) {
     def now = System.currentTimeMillis()
     val start = now
     val end = start + timeout.toUnit(TimeUnit.MILLISECONDS)

     def evalFunction(f: ⇒ Boolean) = {
       try f
       catch { case e: Exception ⇒ false }
     }

     while (now <= end && !evalFunction(func)) {
       Thread.sleep(pause.toMillis)
     }

     val elapsed = (now - start) / 1000.0
     if (now > end) {
       throw new TimeoutException(s"Timed out after ${elapsed} seconds while waiting until ${description}")
     }
   }

   /**
    * Waits until a given test function completes without throwing an exception. This is intended to allow Scalatest matchers
    * to be used in the test function and be able to see the value comparison on failures.
    * If the test function does not complete without throwing an exception before the timeout, a TestTimeoutException is
    * thrown which describes the failure and provides the last exception thrown from the test function.
    * @param description what we're waiting for
    * @param timeout maximum time to wait
    * @param pause time to wait between attempts
    * @param func the test function to evaluate
    * @throws A TestTimeoutException describing the failure and the last exception thrown from the test function
    */
   def retryUntil(description: String, timeout: Duration, pause: Duration = 100 milliseconds)(func: ⇒ Unit) {
     def now = System.currentTimeMillis()
     val start = now
     val end = start + timeout.toUnit(TimeUnit.MILLISECONDS)

     def evalFunction(f: ⇒ Unit) = {
       scala.util.control.Exception.allCatch either f
     }

     var lastResult = evalFunction(func)
     while (now <= end && lastResult.isLeft) {
       Thread.sleep(pause.toMillis)
       lastResult = evalFunction(func)
     }

     val elapsed = (now - start) / 1000.0
     if (now > end) {
       throw new TestTimeoutException(description, elapsed, lastResult.left.get)
     }
   }

   def nextIdOpt = Some(ObjectId())
   def nextId = ObjectId()

   /**
    * Creates a test actor and sends it a message through the event stream until the actor
    * receives the first message so that we can be sure that the event stream is actively listening.
    */
   def waitForEventStreamToBeReady(implicit actorSystem: ActorSystem) {
     case class Ping()

     val sem = new Semaphore(1)
     sem.acquire

     class TestActor(system: ActorSystem) extends Actor {

       def receive = {
         case msg @ _ ⇒ {
           sem.release
           self ! PoisonPill
         }
       }
     }

     val testActor = actorSystem.actorOf(Props(new TestActor(actorSystem)))
     actorSystem.eventStream.subscribe(testActor, classOf[Ping])

     try {
       waitUntil("actor receives first message", 60 seconds) {
         actorSystem.eventStream.publish(Ping())
         sem.availablePermits < 1
       }
     }
     catch {
       case e: Exception ⇒ {
         actorSystem.eventStream.notify()
       }
     }
   }

   /**
    * Shorthand utility method to publish events to the event stream.
    * @param event event ot publish
    * @param actorSystem implicit actor system
    */
   def publish(event: AnyRef)(implicit actorSystem: ActorSystem) {
     actorSystem.eventStream.publish(event)
   }

 }

