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
import reactivemongo.bson.{BSONDocumentWriter, BSONObjectID, BSONDocument, BSONDocumentReader}
import reactivemongo.core.commands.{GetLastError, LastError}

import scala.concurrent.Future

object DistributionProvider extends Enumeration {
  val BINTRAY = Value("Bintray")
  val DOCKERHUB = Value("Dockerhub")
  val UNKNOWN = Value("Unknown")
}

case class DistributionAccount(id: String,
                   clientId: String,
                   name: String,
                   username: String,
                   keyPassword: String,
                   email: Option[String],
                   provider: DistributionProvider.Value,
                   description: Option[String],
                   createdAt: Option[DateTime] = None,
                   updatedAt: Option[DateTime] = None)

object DistributionAccountRepository extends RepositorySupport {

  import scala.concurrent.ExecutionContext.Implicits.global

  val collectionName: String = "distribution-account"

  private def getCollection(implicit system: ActorSystem, database : DB) : BSONCollection = {
    database.collection[BSONCollection](collectionName)
  }

  private def getDistributionProvider(provider: String) : DistributionProvider.Value = {
    var distributionProvider: DistributionProvider.Value = DistributionProvider.UNKNOWN
    try {
      distributionProvider = DistributionProvider.withName(provider)
    }
    catch {
      case e: NoSuchElementException => {
        // fall through with unknown role
      }
    }
    distributionProvider
  }


  implicit object DistributionAccountReader extends BSONDocumentReader[DistributionAccount] {
    def read(doc: BSONDocument): DistributionAccount = {
      DistributionAccount(
        doc.getAs[BSONObjectID]("_id").get.stringify,
        doc.getAs[BSONObjectID]("clientId").get.stringify,
        RepositorySupport.defaultNullField(doc.getAs[String]("name"), "n/a"),
        RepositorySupport.defaultNullField(doc.getAs[String]("username"), "n/a"),
        RepositorySupport.defaultNullField(doc.getAs[String]("keyPassword"), "n/a"),
        doc.getAs[String]("email"),
        getDistributionProvider(RepositorySupport.defaultNullField(doc.getAs[String]("provider"), "")),
        doc.getAs[String]("description"),
        doc.getAs[DateTime]("createdAt"),
        doc.getAs[DateTime]("updatedAt")
      )
    }
  }

  implicit object DistributionAccountWriter extends BSONDocumentWriter[DistributionAccount] {
    def write(distributionAccount: DistributionAccount): BSONDocument = {
      BSONDocument(
        "_id" -> BSONObjectID(distributionAccount.id),
        "clientId" -> BSONObjectID(distributionAccount.clientId),
        "name" -> distributionAccount.name,
        "username" -> distributionAccount.username,
        "keyPassword" -> distributionAccount.keyPassword,
        "email" -> distributionAccount.email,
        "provider" -> distributionAccount.provider.toString,
        "description" -> distributionAccount.description,
        "createdAt" -> distributionAccount.createdAt,
        "updatedAt" -> distributionAccount.updatedAt
      )
    }
  }

  def findByClientId(clientId: String, id: String)(implicit system: ActorSystem, database : DB): Future[Option[DistributionAccount]] = {
    try {
      getCollection.find(BSONDocument("clientId" -> BSONObjectID(clientId), "_id" -> BSONObjectID(id))).cursor[DistributionAccount].headOption
    }
    catch {
      case e: Throwable  => { Future.failed(new DistributionAccountInvalidIdException)}
    }
  }

  def findByName(clientId: String, name: String)(implicit system: ActorSystem, database : DB): Future[Option[DistributionAccount]] = {
    try {
      getCollection.find(BSONDocument("clientId" -> BSONObjectID(clientId), "name" -> name)).cursor[DistributionAccount].headOption
    }
    catch {
      case e: Throwable  => { Future.failed(new DistributionAccountInvalidClientIdException)}
    }
  }

  def findByProvider(clientId: String, provider: String)(implicit system: ActorSystem, database : DB): Future[Option[DistributionAccount]] = {
    try {
      getCollection.find(BSONDocument("clientId" -> BSONObjectID(clientId), "provider" -> provider)).cursor[DistributionAccount].headOption
    }
    catch {
      case e: Throwable  => { Future.failed(new DistributionAccountInvalidClientIdException)}
    }
  }

  def findAllByClientId(clientId: String)(implicit system: ActorSystem, database : DB): Future[List[DistributionAccount]] = {
    try {
      getCollection.find(BSONDocument("clientId" -> BSONObjectID(clientId))).cursor[DistributionAccount].collect[List]()
    }
    catch {
      case e: Throwable  => { Future.failed(new DistributionAccountInvalidClientIdException)}
    }
  }

  def create(account: DistributionAccount)(implicit system: ActorSystem, database : DB): Future[WriteResult] = {
    getCollection.insert(account)
  }

  def update(account: DistributionAccount)(implicit system: ActorSystem, database : DB): Future[WriteResult] = {
    getCollection.update(BSONDocument("_id" -> BSONObjectID(account.id)), account)
  }

  def delete(account: DistributionAccount)(implicit system: ActorSystem, database : DB): Future[WriteResult] = {
    getCollection.remove(account)
  }
}

class DistributionAccountException(msg: String = "distribution account exception") extends RuntimeException(msg)
class DistributionAccountInvalidIdException(msg: String = "invalid distribution account id") extends DistributionAccountException(msg)
class DistributionAccountInvalidClientIdException(msg: String = "invalid client id") extends DistributionAccountException(msg)

