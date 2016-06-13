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

import de.flapdoodle.embed.mongo.config.{MongodConfigBuilder, Net, RuntimeConfigBuilder}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.{Command, MongodExecutable, MongodProcess, MongodStarter}
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.io.{NullProcessor, Processors}
import de.flapdoodle.embed.process.runtime.Network
import reactivemongo.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import akka.util.Timeout

trait BootstrapEmbeddedMongo extends TestSupport {
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  implicit val waitPeriod = Timeout(10000 milliseconds)

  private var mongoStarter: MongodStarter = _
  private var mongoExe: MongodExecutable = _
  private var mongod: MongodProcess = _
  var connection: MongoConnection = _
  implicit var database : DB = _

   def DBHostPort: String
   def DBName: String

   private def embedConnectionPort(): Int = { 12345 }

   def startupMongo() {
     // Used to filter out console output messages.
     val processOutput = new ProcessOutput(
       Processors.named("[mongod>]", new NullProcessor),
       Processors.named("[MONGOD>]", new NullProcessor),
       Processors.named("[console>]", new NullProcessor))

     val runtimeConfig = new RuntimeConfigBuilder()
       .defaults(Command.MongoD)
       .processOutput(processOutput)
       .build()

     lazy val network = new Net(embedConnectionPort, Network.localhostIsIPv6())

     // Start mongo instance
     lazy val mongodConfig = new MongodConfigBuilder()
       .version(Version.V3_0_1)
       .net(network)
       .build

     mongoStarter = MongodStarter.getInstance(runtimeConfig)
     mongoExe = mongoStarter.prepare(mongodConfig)

     waitUntil("mongo starts up", 30 seconds, 5 seconds) {
       try {
         mongod = mongoExe.start()
         true
       }
       catch {
         case _: Throwable ⇒ false
       }
     }

     // connect to the local test database with given host and port
     val driver = new MongoDriver
     connection = driver.connection(List(s"$DBHostPort"))
     database = DB(s"$DBName", connection)
   }

   def shutdownMongo() {
     implicit val timeout = 30.seconds
     cleanupMongo()
     waitUntil("Mongo db shuts down", timeout, 1 second) {
       try {
         connection.askClose()
         mongod.stop()
         mongoExe.stop()
         true
       }
       catch {
         case e: Throwable ⇒
           e.printStackTrace()
           false
       }
     }
   }

   private def cleanupMongo() {
     try {
       val db = DB(s"$DBName", connection)
       Await.result(db.drop(), waitPeriod.duration)
     }
     catch {
       case e: Throwable ⇒
     }
   }
 }
