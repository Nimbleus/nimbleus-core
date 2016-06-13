/* Copyright ©2015 Nimbleus LLC. <http://www.nimbleus.com>
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
package com.nimbleus.core.cloud

import akka.actor.ActorSystem
import com.nimbleus.core.persistence.RepositorySupport
import org.joda.time.DateTime
import reactivemongo.api.DB
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson._
import reactivemongo.core.commands.{GetLastError, LastError}

import scala.concurrent.Future

case class AccessToken (id: String, clientId: String, name: String, description: Option[String],
                        provider: String, token: String, createdAt: Option[DateTime],
                        updatedAt: Option[DateTime])

object AccessToken {
  implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
    def read(time: BSONDateTime) = new DateTime(time.value)
    def write(jodaTime: DateTime) = BSONDateTime(jodaTime.getMillis)
  }
  implicit val accessTokenBSONHandler = Macros.handler[AccessToken]
}

object AccessTokenRepository extends RepositorySupport {

  import scala.concurrent.ExecutionContext.Implicits.global

  val collectionName: String = "access-token"

  private def getCollection(implicit system: ActorSystem, database: DB): BSONCollection = {
    database.collection[BSONCollection](collectionName)
  }

  private def getCloudServiceProvider(provider: String): CloudServiceProvider.Value = {
    var cloudProvider: CloudServiceProvider.Value = CloudServiceProvider.UNKNOWN
    try {
      cloudProvider = CloudServiceProvider.withName(provider)
    }
    catch {
      case e: NoSuchElementException => {
        // fall through with unknown role
      }
    }
    cloudProvider
  }

  private def defaultNullField[A](value: Option[A], default: A): A = {
    value match {
      case Some(v) => {
        v
      }
      case None => {
        default
      }
    }
  }

  implicit object AccessTokenReader extends BSONDocumentReader[AccessToken] {
    def read(doc: BSONDocument): AccessToken = {
      AccessToken(
        doc.getAs[BSONObjectID]("_id").get.stringify,
        doc.getAs[BSONObjectID]("clientId").get.stringify,
        RepositorySupport.defaultNullField(doc.getAs[String]("name"), "n/a"),
        doc.getAs[String]("description"),
        RepositorySupport.defaultNullField(doc.getAs[String]("provider"), "n/a"),
        RepositorySupport.defaultNullField(doc.getAs[String]("token"), "n/a"),
        doc.getAs[DateTime]("createdAt"),
        doc.getAs[DateTime]("updatedAt")
      )
    }
  }

  implicit object AccessTokenWriter extends BSONDocumentWriter[AccessToken] {
    def write(accessToken: AccessToken): BSONDocument = {
      BSONDocument(
        "_id" -> BSONObjectID(accessToken.id),
        "clientId" -> BSONObjectID(accessToken.clientId),
        "name" -> accessToken.name,
        "description" -> accessToken.description,
        "provider" -> accessToken.provider,
        "token" -> accessToken.token,
        "createdAt" -> accessToken.createdAt,
        "updatedAt" -> accessToken.updatedAt
      )
    }
  }

  def find(id: String)(implicit system: ActorSystem, database : DB): Future[Option[AccessToken]] = {
    try {
      getCollection.find(BSONDocument("_id" -> BSONObjectID(id))).cursor[AccessToken].headOption
    }
    catch {
      case e: Throwable => { Future.failed(new AccessTokenInvalidIdException) }
    }
  }

  def findByName(clientId: String, provider: String, name: String)(implicit system: ActorSystem, database : DB): Future[Option[AccessToken]] = {
    try {
      getCollection.find(BSONDocument("clientId" -> BSONObjectID(clientId), "provider" -> provider, "name" -> provider)).cursor[AccessToken].headOption
    }
    catch {
      case e: Throwable => { Future.failed(new AccessTokenInvalidClientIdException) }
    }
  }

  def findAll()(implicit system: ActorSystem, database : DB): Future[List[AccessToken]] = {
    getCollection.find(BSONDocument()).cursor[AccessToken].collect[List]()
  }

  def findAllByClient(clientId: String)(implicit system: ActorSystem, database : DB): Future[List[AccessToken]] = {
    try {
      getCollection.find(BSONDocument("clientId" -> BSONObjectID(clientId))).cursor[AccessToken].collect[List]()
    }
    catch {
      case e: Throwable => { Future.failed(new AccessTokenInvalidClientIdException) }
    }
  }

  def findAllByClientProvider(clientId: String, provider: CloudServiceProvider.Value)(implicit system: ActorSystem, database : DB): Future[List[AccessToken]] = {
    try {
      getCollection.find(BSONDocument("clientId" -> BSONObjectID(clientId), "provider" -> provider.toString)).cursor[AccessToken].collect[List]()
    }
    catch {
      case e: Throwable => { Future.failed(new AccessTokenInvalidClientIdException) }
    }
  }

  def create(accessToken: AccessToken)(implicit system: ActorSystem, database : DB): Future[WriteResult] = {
    getCollection.insert(accessToken)
  }

  def update(accessToken: AccessToken)(implicit system: ActorSystem, database : DB): Future[WriteResult] = {
      getCollection.update(BSONDocument("_id" -> BSONObjectID(accessToken.id)), accessToken)
  }

  def delete(accessToken: AccessToken)(implicit system: ActorSystem, database : DB): Future[WriteResult] = {
    getCollection.remove(accessToken)
  }
}

class AccessTokenException(msg: String = "access token exception") extends RuntimeException(msg)
class AccessTokenInvalidIdException(msg: String = "invalid access token id") extends AccessTokenException(msg)
class AccessTokenInvalidClientIdException(msg: String = "invalid client id") extends AccessTokenException(msg)
class AccessTokenInvalidIdOrClientIdException(msg: String = "invalid access token and/or client id") extends AccessTokenException(msg)
class AccessTokenInvalidClientIdOrProviderOrNameException(msg: String = "invalid client id, provider, or name") extends AccessTokenException(msg)
