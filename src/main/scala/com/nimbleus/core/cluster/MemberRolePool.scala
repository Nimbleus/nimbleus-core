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
package com.nimbleus.core.cluster

import akka.cluster.Member
import scala.collection.mutable.Set
import scala.collection.immutable
import java.util.Random
import akka.actor.{ActorSystem, ActorRef}
import akka.util.Timeout
import scala.util.{Failure, Success}
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

object MemberRole extends Enumeration {
  val NACREOUS_CLOUD_CONTROLLER = Value("nacreous-cloud-controller")
  val NACREOUS_CLIENT_CONTROLLER = Value("nacreous-client-controller")
  val NACREOUS_CLUSTER_CONTROLLER = Value("nacreous-cluster-controller")
  val CINDER_CLIENT = Value("cinder-client")
  val CINDER_CLOUD = Value("cinder-cloud")
  val CINDER_KEYS = Value("cinder-keys")
  val CINDER_BLOCKCHAIN = Value("cinder-blockchain")
}

trait MemberPool {
  def addMember(member : Member) : Unit
  def addMembers(newMembers : scala.collection.immutable.Set[Member]) : Unit
  def hasMember(role: MemberRole.Value) : Boolean
  def getAllMembers : scala.collection.immutable.Set[Member]
  def getAllMembers(role: MemberRole.Value) : scala.collection.immutable.Set[Member]
  def getRandomMember(role: MemberRole.Value) : Option[Member]
  def removeMember(member: Member) : Boolean
  def removeAllMembers : Unit
  def removeAllMembers(role: MemberRole.Value) : Unit
}

object MemberRoutingPool extends MemberPool {
  private val BASE_PATH = "/user/process-supervisor/"
  val members: Set[Member] = Set.empty[Member]

  def addMember(member: Member): Unit = members.add(member)

  def addMembers(newMembers: immutable.Set[Member]): Unit = newMembers.map(members.add(_))

  def hasMember(role: MemberRole.Value) : Boolean = members.filter(_.hasRole(role.toString)).nonEmpty

  def getAllMembers: immutable.Set[Member] = members.toSet

  def getAllMembers(role: MemberRole.Value): immutable.Set[Member] = members.filter(_.hasRole(role.toString)).toSet

  def getRandomMember(role: MemberRole.Value): Option[Member] = {
    val byRoles = members.filter(_.hasRole(role.toString)).toList
    println("selected random member for role " + role.toString + " with members list " + byRoles.toString)
    byRoles.size match {
      case 0 => {None}
      case 1 => {Some(byRoles(0))}
      case _ => {
        Some(byRoles(new Random(System.currentTimeMillis()).nextInt(byRoles.size)))
      }
    }
  }

  def removeMember(member: Member): Boolean = members.remove(member)

  def removeAllMembers: Unit = members.empty

  def removeAllMembers(role: MemberRole.Value): Unit = members.filter(_.hasRole(role.toString)).map(members.remove(_))

  def getMemberRefForRole(role: MemberRole.Value)(implicit system: ActorSystem) : Option[ActorRef] = {
    implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
    if (MemberRoutingPool.hasMember(role)) {
      MemberRoutingPool.getRandomMember(role) match {
        case Some(member) => {
          println("selected member " + member.address.toString + "with role " + role.toString)
          val future : Future[ActorRef] = system.actorSelection(member.address.toString + BASE_PATH + role).resolveOne()
          try {
            val actorRef = Await.result(future, timeout.duration)
            Some (actorRef)
          }
          catch {
            case e : Throwable => { None }
          }
        }
        case None => { None }
      }
    } else { None }
  }
}


