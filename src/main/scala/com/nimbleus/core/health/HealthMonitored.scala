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
package com.nimbleus.core.health

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import com.typesafe.config.ConfigFactory
import scredis._
import scala.util.{ Success, Failure }
import scala.concurrent.ExecutionContext.Implicits.global

object HealthMonitoredQuality {
  val QUALITY_EXCELLENT = "excellent"
  val QUALITY_GOOD = "good"
  val QUALITY_BAD = "bad"
}

object HealthMonitorPrefixes {
  val HEALTH_PREFIX = "health"
}

object HealthMonitorFields {
  val HEALTH_FIELD_PID = "pid"
  val HEALTH_FIELD_TS = "timestamp"
}

object HealthService {
  val config = ConfigFactory.load
  val pwd = config.getString("health-cache-password")
  val environment = config.getString("nimbleus-environment")
  val ttl = config.getInt("health-cache-ttl")
  val client= Redis(host = config.getString("health-cache-url"), port = config.getInt("health-cache-port"), passwordOpt = Some(pwd))

  def heartbeat(applicationId: String, timestamp: DateTime) : Unit = {
    client.auth(pwd) onComplete {
      case Success(result)  => {
        val health = Map(HealthMonitorFields.HEALTH_FIELD_TS -> ISODateTimeFormat.dateTime().print(timestamp))
        client.hmSet(HealthMonitorPrefixes.HEALTH_PREFIX + "-" + environment + "-" + applicationId, health) onComplete {
          case Success(result)  => {
             // now set the expiration
            client.expire(HealthMonitorPrefixes.HEALTH_PREFIX + "-" + environment + "-" + applicationId, ttl) onComplete {
              case Success(result)  => {  }
              case Failure(failure) => {  }
            }
          }
          case Failure(failure) => { }
        }
      }
      case Failure(failure) => {  }
    }
  }
}

/**
 * This trait adds health service monitoring signatures and behavior to implementing classes.
 */
trait HealthMonitored {
  /**
   * Called to write a heartbeat to the external health system.
   * @param applicationId the application sending the heartbeat.
   * @param timestamp the timestamp of the heartbeat.
   */
  def heartbeat(applicationId: String, timestamp: DateTime) : Unit = {
    HealthService.heartbeat(applicationId, timestamp)
  }
}

