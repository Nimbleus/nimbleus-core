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

import scala.collection.immutable
import scala.collection.mutable.ListBuffer

trait UserError
case class EmailInUse() extends UserError
case class UnknownUserError(message: String) extends UserError
case class InvalidUserInfo(message: String) extends UserError
case class PlanError(message: String) extends UserError
case class CardError(message: String) extends UserError
case class InvoiceError(message: String) extends UserError

case class AWSCredentials(awsAccessKeyId: String, awsSecretAccessKey: String, awsKeyPairName: String, awsSecurityGroups: List[String]) {
  def toMap = {
    immutable.Map("awsAccessKeyId" -> awsAccessKeyId, "awsSecretAccessKey" -> awsSecretAccessKey, "awsKeyPairName" -> awsKeyPairName,
      "awsSecurityGroups" -> awsSecurityGroups.mkString(","))
  }
}
object AWSCredentials {
  def getKeyFields : List[String ] = { List("awsAccessKeyId", "awsSecretAccessKey", "awsKeyPairName", "awsSecurityGroups") }
}

case class UserDTO(username: String, firstName: String, lastName: String, email: String,
                   href: Option[String], digitalOceanAccessToken: Option[String],
                   awsCredentials : Option[AWSCredentials]) {
  def validateUserInfo(password: String) : Option[InvalidUserInfo] = {
    val errors : ListBuffer[String] = new ListBuffer[String]()
    if (firstName.isEmpty) { errors += " first name must not be empty" }
    if (lastName.isEmpty) { errors += " last name must not be empty" }
    if (username.isEmpty) { errors += " username must not be empty" }
    if (email.isEmpty) { errors += " email must not be empty" }
    if (!email.matches("(\\w+)@([\\w\\.]+)")) { errors += " invalid email format" }
    if (!password.matches("((?=.*[a-z])(?=.*\\d)(?=.*[A-Z]).{8,100})")) { errors += " password must be between 8 and 100 characters with at least one uppercase and one digit" }
    if (errors.length > 0) { Some(InvalidUserInfo(errors.mkString(",").trim)) } else { None }
  }
}

case class User(cid: String, token: String, username: String, roles: List[String],
                        firstName: String, lastName: String, email: String, href: String,
                        customerId: String, plan: SubscriptionPlan.Value,
                        digitalOceanAccessToken: Option[String], awsCredentials : Option[AWSCredentials]) {
  def isAdmin() : Boolean = {
    roles.contains("administrator")
  }
}
object User {
  type UserResult = Either[UserError, User]
  val USER_FIELD_CID = "cid"
  val USER_FIELD_CUSTOMER_ID = "customer_id"
  val USER_FIELD_USERNAME = "username"
  val USER_FIELD_FIRSTNAME = "firstname"
  val USER_FIELD_LASTNAME = "lastname"
  val USER_FIELD_EMAIL = "email"
  val USER_FIELD_HREF = "href"
  val USER_FIELD_DIGITAL_OCEAN_ACCESS_TOKEN = "do_access_token"
  def getToken = java.util.UUID.randomUUID.toString
}
sealed case class SignOnCredentials(login: String, password: String)







