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

import com.nimbleus.core.security.User.UserResult
import scala.concurrent.{Promise, Future}
import com.stormpath.sdk.application._
import com.stormpath.sdk.account._
import com.stormpath.sdk.authc._
import com.stormpath.sdk.resource.ResourceException
import com.stormpath.sdk.client._
import java.util.Properties
import com.typesafe.config.{ConfigException, ConfigFactory}
import com.stormpath.sdk.group.Group
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import com.stormpath.sdk.directory.{Directory, CustomData}
import reactivemongo.bson.BSONObjectID
import com.stripe.Stripe
import com.stripe.model._
import scala.collection.JavaConversions._
import com.stormpath.sdk.account.Account

object UserManagementService {
  private val CLIENT_ID = "cid"
  private val CUSTOMER_ID = "customer_id"
  private val DIGITAL_OCEAN_ACCESS_TOKEN = "do_access_token"
  private val AWS_CREDENTIALS = "aws_credentials"
  private var properties: Properties = new Properties()
  val config = ConfigFactory.load
  properties.setProperty("apiKey.id", config.getString("security-api-key"))
  properties.setProperty("apiKey.secret", config.getString("security-api-secret"))
  private val apiKey: ApiKey = ApiKeys.builder().setProperties(properties).build()
  private val client: Client  = Clients.builder().setApiKey(apiKey).build()
  private val applicationHref = config.getString("stormpath-application-href")
  private val directoryHref = config.getString("stormpath-directory-href")
  private val application: Application = client.getResource(applicationHref, classOf[Application])

  // TODO this is a temp fix
  private def getSafeString(key: String) : String = {
    val s = try {
      config.getString(key)
    }
    catch {
      case e : ConfigException.Missing  => { "" }
    }
    s
  }
  Stripe.apiKey = getSafeString("stripe-api-key")

  private def getSubscriptionPlan(customerId: String) : SubscriptionPlan.Value = {
    if (customerId == null) {
      { SubscriptionPlan.NACREOUS_DEVELOPER }
    } else {
      try {
        println("resolving user subscription")
        val customer: Customer = Customer.retrieve(customerId)
        if (customer.getSubscriptions != null) {
          val subs: CustomerSubscriptionCollection  = customer.getSubscriptions().all(null)
          if (subs.getCount > 0) {
            val subscription : Subscription = subs.getData.get(0)
            SubscriptionPlan.withName(subscription.getPlan.getId)
          } else { SubscriptionPlan.NACREOUS_DEVELOPER }
        } else { SubscriptionPlan.NACREOUS_DEVELOPER }
      }
      catch {
        case e: NoSuchElementException => {
          { SubscriptionPlan.NACREOUS_DEVELOPER }
        }
      }
    }
  }

  def authenticate(username: String, password: String): Option[User] = {
    val request = new UsernamePasswordRequest(username, password)
    try {
      val account: Account = application.authenticateAccount(request).getAccount
      val groups: List[Group]  = account.getGroups.asScala.toList
      var roles: collection.mutable.ListBuffer[String] = ListBuffer()
      for (g <- groups) {roles.append(g.getName)}
      // handle edge case of now unique id associated with the user
      val customData: CustomData  = account.getCustomData()
      val cid: String = customData.get(CLIENT_ID).asInstanceOf[String]
      val customerId: String = customData.get(CUSTOMER_ID).asInstanceOf[String]
      val digitalOceanAccessToken: Option[String] = Option(customData.get(DIGITAL_OCEAN_ACCESS_TOKEN).asInstanceOf[String])

      val awsAccessKeyId: Option[String] = Option(customData.get("awsAccessKeyId").asInstanceOf[String])
      val awsSecretAccessKey: Option[String] = Option(customData.get("awsSecretAccessKey").asInstanceOf[String])
      val awsKeyPairName: Option[String] = Option(customData.get("awsKeyPairName").asInstanceOf[String])
      val awsSecurityGroups: Option[String] = Option(customData.get("awsSecurityGroups").asInstanceOf[String])

      val sec = if (awsSecurityGroups.isDefined) {
        awsSecurityGroups.get.split(",").toList
      } else {
        List.empty
      }

      val awsCredentials = AWSCredentials(awsAccessKeyId.getOrElse(""), awsSecretAccessKey.getOrElse(""),
        awsKeyPairName.getOrElse(""), sec)
      val saveId = if (cid == null) {
        val newCid = BSONObjectID.generate.stringify
        customData.put(CLIENT_ID, newCid)
        customData.save()
        newCid
      } else {
        cid
      }
      Some(User(saveId, User.getToken, account.getUsername, roles.toList, account.getGivenName,
        account.getSurname, account.getEmail, account.getHref, customerId, getSubscriptionPlan(customerId),
        digitalOceanAccessToken, Some(awsCredentials)))}
    catch {
      case e: ResourceException  => {
        println(e.getMessage)
        None // authentication failed
      }
    }
    finally {request.clear()}
  }

  def createUser(user: UserDTO, password: String) : Future[UserResult] = {
    val result = Promise[UserResult]
    user.validateUserInfo(password) match {
      case Some(errors) => {
        result.success(Left(errors))
      }
      case None => {
        try {
          val criteria: AccountCriteria = Accounts.where(Accounts.email().eqIgnoreCase(user.email))
          val accounts: AccountList = application.getAccounts(criteria)
          if (accounts.iterator().hasNext) {
            // this account exists so error out
            result.success(Left(EmailInUse()))
          }
          else {
            val account: Account = client.instantiate(classOf[Account])
            account.setEmail(user.email)
            account.setGivenName(user.firstName)
            println(password)
            account.setPassword(password)
            account.setSurname(user.lastName)
            account.setUsername(user.email)
            val directory: Directory = client.getResource(directoryHref, classOf[Directory])
            directory.createAccount(account)
            val customData: CustomData = account.getCustomData()
            val newCid = BSONObjectID.generate.stringify
            customData.put(CLIENT_ID, newCid)
            // create a subscription for this new user
            val customerWithPlanParams : Map[String, Object] = Map("email" -> user.email, "plan" -> SubscriptionPlan.NACREOUS_DEVELOPER.toString)
            val customer : Customer = Customer.create(customerWithPlanParams)
            customData.put(CUSTOMER_ID, customer.getId)
            customData.save
            result.success(Right(User(newCid, User.getToken, account.getUsername, List.empty, account.getGivenName,
              account.getSurname, account.getEmail, account.getHref, customer.getId,
              SubscriptionPlan.NACREOUS_DEVELOPER, None, None)))
          }
        }
        catch {
          case e: Throwable => {
            println(e.getMessage)
            result.success(Left(UnknownUserError(e.getMessage)))
          }
        }
      }
    }
    result.future
  }

  // TODO this really is not a future
  def deleteUser(user: User) : Future[Boolean] = {
    val result = Promise[Boolean]
    try {
      val account: Account = client.getResource(user.href, classOf[Account])
      account.delete
      // if there is a customer account then delete it
      if (user.customerId.nonEmpty) {
        try {
          // delete the customer subscription, will laso delete all subscriptions
          val customer: Customer = Customer.retrieve(user.customerId)
          customer.delete
        }
        catch {
          case e : Throwable => {println(e.getMessage)}
        }
      }
      result.success(true)
    }
    catch {
      case e: Throwable => {
        println(e.getMessage)
        result.success(false)
      }
    }
    result.future
  }

  def updateUser(user: UserDTO, password: Option[String]) : Future[UserResult] = {
    val result = Promise[UserResult]
    try {
      val account: Account = client.getResource(user.href.getOrElse(""), classOf[Account])
      account.setSurname(user.lastName)
      account.setGivenName(user.firstName)
      password match {
        case Some(pwd) => {account.setPassword(pwd)}
        case None => {}
      }
      account.save()
      // now return this account
      val groups: List[Group]  = account.getGroups.asScala.toList
      var roles: collection.mutable.ListBuffer[String] = ListBuffer()
      for (g <- groups) {roles.append(g.getName)}
      val customData: CustomData  = account.getCustomData()
      // update the custom fields if needed
      user.digitalOceanAccessToken match {
        case Some(accessToken) => {
          customData.put(DIGITAL_OCEAN_ACCESS_TOKEN, accessToken)
        }
        case None => {customData.remove(DIGITAL_OCEAN_ACCESS_TOKEN)}
      }
      user.awsCredentials match {
        case Some(awsCredentials) => {
          val awsCreds = awsCredentials.toMap
          awsCreds.foreach {case(key, value) =>
            customData.put(key, value)
          }
        }
        case None => { customData.remove(AWS_CREDENTIALS) }
      }

      customData.save
      // now reset user instance fields
      val cid: String = customData.get(CLIENT_ID).asInstanceOf[String]
      val customerId: String = customData.get(CUSTOMER_ID).asInstanceOf[String]
      val digitalOceanAccessToken: Option[String] = Option(customData.get(DIGITAL_OCEAN_ACCESS_TOKEN).asInstanceOf[String])

      val awsAccessKeyId: Option[String] = Option(customData.get("awsAccessKeyId").asInstanceOf[String])
      val awsSecretAccessKey: Option[String] = Option(customData.get("awsSecretAccessKey").asInstanceOf[String])
      val awsKeyPairName: Option[String] = Option(customData.get("awsKeyPairName").asInstanceOf[String])
      val awsSecurityGroups: Option[String] = Option(customData.get("awsSecurityGroups").asInstanceOf[String])

      val sec = if (awsSecurityGroups.isDefined) {
        awsSecurityGroups.get.split(",").toList
      } else {
        List.empty
      }

      val awsCredentials = AWSCredentials(awsAccessKeyId.getOrElse(""), awsSecretAccessKey.getOrElse(""),
        awsKeyPairName.getOrElse(""), sec)

      val updatedUser = User(cid, User.getToken, account.getUsername, List.empty, account.getGivenName,
        account.getSurname, account.getEmail, account.getHref, customerId, getSubscriptionPlan(customerId),
        digitalOceanAccessToken, Some(awsCredentials))
      result.success(Right(updatedUser))
    }
    catch {
      case e: Throwable => {
        println(e.getMessage)
        result.success(Left(UnknownUserError(e.getMessage)))
      }
    }
    result.future
  }

  // sends an email to the user
  def sendPasswordResetEmail(email: String) : Future[Boolean] = {
    val result = Promise[Boolean]
    try {
      val criteria: AccountCriteria = Accounts.where(Accounts.email().eqIgnoreCase(email))
      val accounts: AccountList = application.getAccounts(criteria)
      if (accounts.iterator().hasNext) {
        // this account exists so error out
        application.sendPasswordResetEmail(email)
        result.success(true)
      }
      else {
      result.success(false)
      }
    }
    catch {
      case e: Throwable => {
        println(e.getMessage)
        result.success(false)
      }
    }
    result.future
  }

  def verifyPasswordResetToken(token: String, password: String) : Future[UserResult] = {
    val result = Promise[UserResult]
    try {
      val account: Account = application.verifyPasswordResetToken(token)
      account.setPassword(password)
      account.save()
      val groups: List[Group]  = account.getGroups.asScala.toList
      var roles: collection.mutable.ListBuffer[String] = ListBuffer()
      for (g <- groups) {roles.append(g.getName)}
      val customData: CustomData  = account.getCustomData()
      val cid: String = customData.get(CLIENT_ID).asInstanceOf[String]
      val customerId: String = customData.get(CUSTOMER_ID).asInstanceOf[String]
      val digitalOceanAccessToken: Option[String] = Option(customData.get(DIGITAL_OCEAN_ACCESS_TOKEN).asInstanceOf[String])
      val awsAccessKeyId: Option[String] = Option(customData.get("awsAccessKeyId").asInstanceOf[String])
      val awsSecretAccessKey: Option[String] = Option(customData.get("awsSecretAccessKey").asInstanceOf[String])
      val awsKeyPairName: Option[String] = Option(customData.get("awsKeyPairName").asInstanceOf[String])
      val awsSecurityGroups: Option[String] = Option(customData.get("awsSecurityGroups").asInstanceOf[String])

      val sec = if (awsSecurityGroups.isDefined) {
        awsSecurityGroups.get.split(",").toList
      } else {
        List.empty
      }

      val awsCredentials = AWSCredentials(awsAccessKeyId.getOrElse(""), awsSecretAccessKey.getOrElse(""),
        awsKeyPairName.getOrElse(""), sec)
      result.success(Right(User(cid, User.getToken, account.getUsername, List.empty, account.getGivenName,
        account.getSurname, account.getEmail, account.getHref, customerId, getSubscriptionPlan(customerId),
        digitalOceanAccessToken, Some(awsCredentials))))
    }
    catch {
      case e: Throwable => {
        println(e.getMessage)
        result.success(Left(UnknownUserError(e.getMessage)))
      }
    }
    result.future
  }

  def verifyAccountEmail(token: String) : Future[UserResult] = {
    val result = Promise[UserResult]
    try {
      val account: Account = client.getCurrentTenant.verifyAccountEmail(token)
      // now return this account
      val groups: List[Group]  = account.getGroups.asScala.toList
      var roles: collection.mutable.ListBuffer[String] = ListBuffer()
      for (g <- groups) {roles.append(g.getName)}
      val customData: CustomData  = account.getCustomData()
      val cid: String = customData.get(CLIENT_ID).asInstanceOf[String]
      val customerId: String = customData.get(CUSTOMER_ID).asInstanceOf[String]
      val digitalOceanAccessToken: Option[String] = Option(customData.get(DIGITAL_OCEAN_ACCESS_TOKEN).asInstanceOf[String])
      val awsAccessKeyId: Option[String] = Option(customData.get("awsAccessKeyId").asInstanceOf[String])
      val awsSecretAccessKey: Option[String] = Option(customData.get("awsSecretAccessKey").asInstanceOf[String])
      val awsKeyPairName: Option[String] = Option(customData.get("awsKeyPairName").asInstanceOf[String])
      val awsSecurityGroups: Option[String] = Option(customData.get("awsSecurityGroups").asInstanceOf[String])

      val sec = if (awsSecurityGroups.isDefined) {
        awsSecurityGroups.get.split(",").toList
      } else {
        List.empty
      }

      val awsCredentials = AWSCredentials(awsAccessKeyId.getOrElse(""), awsSecretAccessKey.getOrElse(""),
        awsKeyPairName.getOrElse(""), sec)
      result.success(Right(User(cid, User.getToken, account.getUsername, List.empty, account.getGivenName,
        account.getSurname, account.getEmail, account.getHref, customerId, getSubscriptionPlan(customerId),
        digitalOceanAccessToken, Some(awsCredentials))))
    }
    catch {
      case e: Throwable => {
        println(e.getMessage)
        result.success(Left(UnknownUserError(e.getMessage)))}
    }
    result.future
  }

  private def getCardData(card : CustomerCard) : Map[String, Object] = {
    Map[String, Object]("number" -> card.number,
    "exp_month" -> card.expMonth,
    "exp_year" -> card.expYear,
    "cvc" -> card.cvc,
    "name" -> card.name,
    "address_line1" -> card.address1,
    "address_line2" -> card.address2,
    "address_zip" -> card.zip,
    "address_state" -> card.state,
    "address_country" -> card.country)
  }

  def getCreditCard(user: User, cardId: String) : Either[UserError, CustomerCard] = {
    try {
      val customer: Customer = Customer.retrieve(user.customerId)
      Right(CustomerCard.getCustomerCard(customer.getCards.retrieve(cardId)))
    }
    catch {
      case e: Throwable => {
        { Left(CardError(e.getMessage)) }
      }
    }
  }

  def getCreditCards(user: User) : Either[UserError, List[CustomerCard]] = {
    try {
      val customer: Customer = Customer.retrieve(user.customerId)
      Right(customer.getCards.getData.asScala.toList.map(c => CustomerCard.getCustomerCard(c)))
    }
    catch {
      case e: Throwable => {
        { Left(CardError(e.getMessage)) }
      }
    }
  }

  def addCreditCard(user: User, card : CustomerCard, default: Boolean) : Either[UserError, CustomerCard] = {
    try {
      val customer: Customer = Customer.retrieve(user.customerId)
      val newCard = customer.createCard(getCardData(card))
      if (default) { customer.setDefaultCard(newCard.getId)}
      Right(CustomerCard.getCustomerCard(newCard))
    }
    catch {
      case e: Throwable => {
        { Left(CardError(e.getMessage)) }
      }
    }
  }

  def updateCreditCard (user: User, cardId: String, cardData: CustomerCard) : Either[UserError, CustomerCard] = {
    try {
      val customer: Customer = Customer.retrieve(user.customerId)
      val targetCard = customer.getCards.retrieve(cardId)
      targetCard.update(cardData.getMutableData)
      Right(CustomerCard.getCustomerCard(targetCard))
    }
    catch {
      case e: Throwable => {
        { Left(CardError(e.getMessage)) }
      }
    }
  }

  def removeCreditCard(user: User, cardId : String) : Either[UserError, CustomerCard] = {
    try {
      val customer: Customer = Customer.retrieve(user.customerId)
      if ((customer.getCards.all(null).getCount == 1) && (SubscriptionPlan.isPaidAccount(user.plan))) {
        Left(CardError("Premium Subscription requires at least one card"))
      } else {
        val card = customer.getCards.retrieve(cardId)
        card.delete()
        Right(CustomerCard.getCustomerCard(card))
      }
    }
    catch {
      case e: Throwable => {
        { Left(CardError(e.getMessage)) }
      }
    }
  }

  def changePlan(user: User, newPlan : SubscriptionPlan.Value, card : Option[CustomerCard]) : UserResult = {
    if (user.plan.equals(newPlan)) { Left(PlanError("User is already subscribed to plan")) } else {
      try {
        val customer: Customer = Customer.retrieve(user.customerId)
        if (card.isDefined) {
          customer.createCard(getCardData(card.get))
        } else {
          if ((SubscriptionPlan.isPaidAccount(newPlan)) && (customer.getDefaultCard.equals("") || customer.getDefaultCard == null)) {
            Left(PlanError("Premium subscription plans require a default card"))
          }
        }
        // now update the plan
        val subs: CustomerSubscriptionCollection  = customer.getSubscriptions().all(null)
        if (subs.getCount > 0) {
          val subscription : Subscription = subs.getData.get(0)
          subscription.getPlan.update(Map("name" -> newPlan.toString))
          Right(user.copy(plan = newPlan))
        } else { Left(PlanError("User subscription not found. please contact support.")) }
      }
      catch {
        case e: Throwable => {
          { Left(PlanError(e.getMessage)) }
        }
      }
    }
  }

  def getInvoices(user: User, limit : Int = 12) : Either[UserError, List[CustomerInvoice]]  = {
    try {
      Right(Invoice.all(Map[String, Object]("customer" -> user.customerId, "count" -> new java.lang.Integer(limit))).getData.asScala.toList.map(i => CustomerInvoice.getCustomerInvoice(i)))
    }
    catch {
      case e: Throwable => {
        { Left(InvoiceError(e.getMessage)) }
      }
    }
  }
}
