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
package com.nimbleus.core.security

import akka.http.scaladsl.model.headers.{HttpCredentials, BasicHttpCredentials, HttpChallenge}
import akka.http.scaladsl.server.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import akka.http.scaladsl.server._

trait UserAuthentication extends Directives {
  protected def sessionStore: SessionStore

  val challenge = HttpChallenge(scheme = "Basic", realm = "Nimbleus", params = Map.empty)

  // TODO use a future
  def authenticateUser(credentials: Option[HttpCredentials]): Directive1[User] = {
    credentials match {
      case Some(c)  => {
        val basicCredentials : BasicHttpCredentials = c.asInstanceOf[BasicHttpCredentials]
        if (basicCredentials.username.isEmpty || basicCredentials.password.isEmpty) {
          reject(AuthenticationFailedRejection(CredentialsMissing, challenge))
        } else {
          UserManagementService.authenticate(basicCredentials.username, basicCredentials.password) match {
            case Some(user) => {
              if (sessionStore.addSession(user.copy())) {
                provide(user)
              } else {
                reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
              }
            }
            case None => {
              // authentication failed
              reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
            }
          }
        }
      }
      case None => {
        reject(AuthenticationFailedRejection(CredentialsMissing, challenge))
      }
    }
  }

  def authenticateToken(token: String): Directive1[User] = {
    sessionStore.getSession(token) match {
      case Some(user) => {
        provide(user)
      }
      case None => {
        reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
      }
    }
  }

  def updateUserInSession(user: User) : Boolean = {
    sessionStore.addSession(user)
  }

  def terminateSession(token: String) : Boolean = {
    sessionStore.expireSession(token)
  }
}
