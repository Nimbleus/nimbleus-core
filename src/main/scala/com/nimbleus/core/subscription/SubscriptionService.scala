package com.nimbleus.core.subscription

import java.util
import com.stripe.Stripe
import com.stripe.model._
import com.typesafe.config.{ConfigException, ConfigFactory}
import org.joda.time.DateTime
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

trait StripConversions {
  def getValue[B](value: Option[B], default: B): B = {
    if (value.isDefined) {
      value.get
    } else {
      default
    }
  }

  def getValueOrDefault[T >: Null <: AnyRef](value: T, default: Option[T] = None): Option[T] = {
    Option(value) match {
      case Some(v) => {
        Some(v)
      }
      case None => {
        if (default.isDefined) {
          default
        } else {
          None
        }
      }
    }
  }

  def getStripeTimestamp(epoch: Long): Long = (epoch * 1000)

  def getDateFromEpoch(date: Option[Long]): Option[DateTime] = {
    date match {
      case Some(d) => {
        Some(new DateTime(getStripeTimestamp(d)))
      }
      case None => {
        None
      }
    }
  }
}

object CustomerLineItemPeriod extends StripConversions {
  def getCustomerLineItemPeriod(period: InvoiceLineItemPeriod): CustomerLineItemPeriod = {
    CustomerLineItemPeriod(getDateFromEpoch(Option(period.getStart).map(_.longValue)), getDateFromEpoch(Option(period.getEnd).map(_.longValue)))
  }
}

case class CustomerLineItemPeriod(start: Option[DateTime], end: Option[DateTime])

object CustomerInvoiceItem extends StripConversions {
  def getCustomerInvoiceItem(item: InvoiceLineItem): CustomerInvoiceItem = {
    CustomerInvoiceItem(item.getId, Option(item.getAmount).map(_.intValue), Option(item.getCurrency),
      Option(item.getProration).map(_.booleanValue), Some(CustomerLineItemPeriod.getCustomerLineItemPeriod(item.getPeriod)), Option(item.getQuantity).map(_.intValue),
      Option(item.getPlan.getId), Option(item.getDescription))
  }
}

case class CustomerInvoiceItem(id: String, amount: Option[Int], currency: Option[String],
                               proration: Option[Boolean], period: Option[CustomerLineItemPeriod], quantity: Option[Int],
                               planId: Option[String], description: Option[String])

object CustomerInvoice extends StripConversions {
  def getCustomerInvoice(invoice: Invoice): CustomerInvoice = {
    CustomerInvoice(invoice.getId, Option(invoice.getSubtotal).map(_.intValue), Option(invoice.getTotal).map(_.intValue), Option(invoice.getAmountDue).map(_.intValue),
      Option(invoice.getCreated).map(_.longValue),
      Option(invoice.getCustomer), getDateFromEpoch(Option(invoice.getDate).map(_.longValue)), Option(invoice.getPaid).map(_.booleanValue),
      getDateFromEpoch(Option(invoice.getPeriodStart).map(_.longValue)),
      getDateFromEpoch(Option(invoice.getPeriodEnd).map(_.longValue)),
      Option(invoice.getCurrency), Option(invoice.getSubscription), invoice.getLines.getData.asScala.toList.map(l => CustomerInvoiceItem.getCustomerInvoiceItem(l)))
  }
}

case class CustomerInvoice(id: String, subtotal: Option[Int], total: Option[Int], amountDue: Option[Int], created: Option[Long],
                           customer: Option[String], date: Option[DateTime], paid: Option[Boolean], periodStart: Option[DateTime], periodEnd: Option[DateTime],
                           currency: Option[String], subscription: Option[String], lines: List[CustomerInvoiceItem])

object CustomerCard extends StripConversions {
  def getCustomerCard(card: Card): CustomerCard = {
    CustomerCard(card.getId, Option(card.getBrand), Option(card.getLast4), Option(card.getExpMonth), Option(card.getExpYear),
      Option(card.getCvcCheck), Option(card.getName), Option(card.getAddressLine1), Option(card.getAddressLine2), Option(card.getAddressCity),
      Option(card.getAddressZip), Option(card.getAddressState), Option(card.getAddressCountry))
  }
}

case class CustomerCard(id: String, brand: Option[String], number: Option[String], expMonth: Option[Int], expYear: Option[Int],
                        cvc: Option[String], name: Option[String], address1: Option[String], address2: Option[String], city:
                        Option[String], zip: Option[String], state: Option[String], country: Option[String])

object UserPlan extends StripConversions {
  def apply(plan: Plan): UserPlan = new UserPlan(plan.getId, Option(plan.getAmount).map(_.intValue), Option(plan.getCurrency),
    Option(plan.getInterval), Option(plan.getIntervalCount).map(_.intValue), Option(plan.getName),
    Option(plan.getTrialPeriodDays).map(_.intValue), Option(plan.getStatementDescriptor))
}

case class UserPlan(id: String, amount: Option[Int], currency: Option[String],
                    interval: Option[String], intervalCount: Option[Int], name: Option[String],
                    trialPeriodDays: Option[Int], statementDescriptor: Option[String])

object UserCoupon extends StripConversions {
  def apply(coupon: Coupon) = {
    new UserCoupon(coupon.getId, Option(coupon.getPercentOff), Option(coupon.getAmountOff), Option(coupon.getCurrency),
      Option(coupon.getDuration), Option(coupon.getDurationInMonths.toLong), Option(coupon.getMaxRedemptions),
      getDateFromEpoch(Option(coupon.getRedeemBy).map(_.longValue)), Option(coupon.getTimesRedeemed), Option(coupon.getValid))
  }
}

case class UserCoupon(id: String, percentOff: Option[Int], amountOff: Option[Int], currency: Option[String],
                      duration: Option[String], durationInMonths: Option[Long], maxRedemptions: Option[Long],
                      redeemBy: Option[DateTime], timesRedeemed: Option[Int], valid: Option[Boolean])

object UserDiscount extends StripConversions {
  def apply(discount: Discount) = {
    new UserDiscount(discount.getId, getDateFromEpoch(Option(discount.getEnd).map(_.longValue)),
      getDateFromEpoch(Option(discount.getStart).map(_.longValue)),
      if (discount.getCoupon == null) {
        None
      } else {
        Some(UserCoupon(discount.getCoupon))
      },
      Option(discount.getCustomer), Option(discount.getSubscription))
  }
}

case class UserDiscount(id: String, end: Option[DateTime], start: Option[DateTime],
                        coupon: Option[UserCoupon], customer: Option[String], subscription: Option[String])

object UserSubscription extends StripConversions {
  def apply(subscription: Subscription) = {
    new UserSubscription(subscription.getId,
      getDateFromEpoch(Option(subscription.getCurrentPeriodEnd).map(_.longValue)),
      getDateFromEpoch(Option(subscription.getCurrentPeriodStart).map(_.longValue)),
      Option(subscription.getCancelAtPeriodEnd),
      Option(subscription.getCustomer),
      getDateFromEpoch(Option(subscription.getStart).map(_.longValue)),
      Option(subscription.getStatus),
      getDateFromEpoch(Option(subscription.getTrialStart).map(_.longValue)),
      getDateFromEpoch(Option(subscription.getTrialEnd).map(_.longValue)),
      if (subscription.getPlan == null) {
        None
      } else {
        Some(UserPlan(subscription.getPlan))
      },
      getDateFromEpoch(Option(subscription.getCanceledAt).map(_.longValue)),
      getDateFromEpoch(Option(subscription.getEndedAt).map(_.longValue)),
      Option(subscription.getQuantity), if (subscription.getDiscount == null) {
        None
      } else {
        Some(UserDiscount(subscription.getDiscount))
      })
  }
}

case class UserSubscription(id: String, currentPeriodEnd: Option[DateTime], currentPeriodStart: Option[DateTime],
                            cancelAtPeriodEnd: Option[Boolean], customer: Option[String], start: Option[DateTime],
                            status: Option[String], trialStart: Option[DateTime], trialEnd: Option[DateTime],
                            plan: Option[UserPlan], canceledAt: Option[DateTime], endedAt: Option[DateTime],
                            quantity: Option[Int], discount: Option[UserDiscount])

trait SubscriptionError

case class PlanError(message: String) extends SubscriptionError

case class CardError(message: String) extends SubscriptionError

case class InvoiceError(message: String) extends SubscriptionError

object SubscriptionService {
  val config = ConfigFactory.load

  private def getSafeString(key: String): String = {
    val s = try {
      config.getString(key)
    }
    catch {
      case e: ConfigException.Missing => {
        ""
      }
    }
    s
  }

  Stripe.apiKey = getSafeString("stripe-api-key")

  def addCreditCard(customerId: String, cardToken: String, default: Boolean = false): Either[SubscriptionError, String] = {
    try {
      val customer: Customer = Customer.retrieve(customerId)
      val params: util.Map[String, Object] = new util.HashMap[String, Object]()
      params.put("source", cardToken)
      val card = customer.getSources().create(params)
      if (default) {
        val updateParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        updateParams.put("default_source", card.getId)
        customer.update(updateParams)
      }
      Right(card.getId)
    }
    catch {
      case e: Throwable => {
        {
          Left(CardError(e.getMessage))
        }
      }
    }
  }

  def removeCreditCard(customerId: String, cardId: String): Either[SubscriptionError, Boolean] = {
    try {
      val customer: Customer = Customer.retrieve(customerId)
      val deletedExternalAccount = customer.getSources().retrieve(cardId).delete
      Right(true)
    }
    catch {
      case e: Throwable => {
        {
          Left(CardError(e.getMessage))
        }
      }
    }
  }

  /**
    * The following fields can be updated.
    *
    * address_city , address_country, address_line1  , address_line2 , address_state , address_zip 
    * default_for_currency, exp_month , exp_year, name
    */
  def updateCreditCard(customerId: String, cardId: String, updateParams: util.Map[String, Object]): Either[SubscriptionError, CustomerCard] = {
    try {
      val customer: Customer = Customer.retrieve(customerId)
      val card: Card = customer.getSources().retrieve(cardId).asInstanceOf[Card]
      val updatedCard = card.update(updateParams)
      Right(CustomerCard.getCustomerCard(updatedCard))
    }
    catch {
      case e: Throwable => {
        {
          Left(CardError(e.getMessage))
        }
      }
    }
  }

  def getCreditCards(customerId: String): Either[SubscriptionError, List[CustomerCard]] = {
    try {
      val customer: Customer = Customer.retrieve(customerId)
      val sourcesParams: Map[String, Object] = Map[String, Object]("object" -> "card")
      val accounts = Customer.retrieve(customerId).getSources().list(sourcesParams)
      Right(accounts.getData.asScala.toList.map(c => CustomerCard.getCustomerCard(c.asInstanceOf[Card])))
    }
    catch {
      case e: Throwable => {
        {
          Left(CardError(e.getMessage))
        }
      }
    }
  }

  def getCreditCard(customerId: String, cardId: String): Either[SubscriptionError, CustomerCard] = {
    try {
      val customer: Customer = Customer.retrieve(customerId)
      val card: Card = customer.getSources().retrieve(cardId).asInstanceOf[Card]
      Right(CustomerCard.getCustomerCard(card))
    }
    catch {
      case e: Throwable => {
        {
          Left(CardError(e.getMessage))
        }
      }
    }
  }

  def changePlan(customerId: String, newPlanId: Option[String]): Either[SubscriptionError, Option[String]] = {
    newPlanId match {
      case Some(plan) => {
        try {
          val customer: Customer = Customer.retrieve(customerId)
          // get the actual card
          val subscriptionParams: util.Map[String, Object] = new util.HashMap[String, Object]()
          subscriptionParams.put("customer", customer.getId)
          val subscriptions = Subscription.list(subscriptionParams)
          if (subscriptions.getCount > 0) {
            // now update the plan
            val updateParams: util.Map[String, Object] = new util.HashMap[String, Object]()
            updateParams.put("plan", plan)
            val subscription: Subscription = subscriptions.getData.get(0).update(updateParams)
            Right(Some(subscription.getId))
          } else {
            // create a new subscription
            val createParams: util.Map[String, Object] = new util.HashMap[String, Object]()
            createParams.put("customer", customer.getId)
            createParams.put("plan", plan)
            val subscription: Subscription = Subscription.create(createParams)
            Right(Some(subscription.getId))
          }
        }
        catch {
          case e: Throwable => {
            Left(PlanError(e.getMessage))
          }
        }
      }
      case None => {
        // no plan was specific so cancel any current plan
        try {
          val customer: Customer = Customer.retrieve(customerId)
          val params: util.Map[String, Object] = new util.HashMap[String, Object]()
          params.put("customer", customer.getId)
          val subsriptions: SubscriptionCollection = Subscription.list(params)
          for (subscription: Subscription <- subsriptions.autoPagingIterable) {
            subscription.cancel(null)
          }
          Right(None)
        } catch {
          case e: Throwable => {
            Left(PlanError(e.getMessage))
          }
        }
      }
    }
  }

  def getSubscriptionPlan(customerId: String): Option[UserSubscription] = {
    if (customerId == null) {
      None
    } else {
      try {
        val customer: Customer = Customer.retrieve(customerId)
        if (customer.getSubscriptions != null) {
          val subs: CustomerSubscriptionCollection = customer.getSubscriptions().all(null)
          if (subs.getCount > 0) {
            val subscription: Subscription = subs.getData.get(0)
            Some(UserSubscription(subscription))
          } else {
            None
          }
        } else {
          None
        }
      }
      catch {
        case e: NoSuchElementException => {
          {
            None
          }
        }
      }
    }
  }

  def getInvoices(customerId: String, limit: Int = 12): Either[SubscriptionError, List[CustomerInvoice]] = {
    try {
      val params: util.Map[String, Object] = new util.HashMap[String, Object]()
      params.put("customer", customerId)
      params.put("count", new java.lang.Integer(limit))
      val invoices = Invoice.list(params)
      Right(invoices.getData.asScala.toList.map(i => CustomerInvoice.getCustomerInvoice(i)))
    }
    catch {
      case e: Throwable => {
        {
          Left(InvoiceError(e.getMessage))
        }
      }
    }
  }
}
