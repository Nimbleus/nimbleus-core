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
package com.nimbleus.core.security

import com.stripe.model.{Card, Invoice, InvoiceLineItem, InvoiceLineItemPeriod}
import scala.collection.JavaConverters._

object CustomerLineItemPeriod {
  def getCustomerLineItemPeriod(period : InvoiceLineItemPeriod) : CustomerLineItemPeriod = {
    CustomerLineItemPeriod(period.getStart, period.getEnd)
  }
}
case class CustomerLineItemPeriod(start : Long, end : Long)

object CustomerInvoiceItem {
    def getCustomerInvoiceItem(item : InvoiceLineItem) : CustomerInvoiceItem = {
      CustomerInvoiceItem(item.getId, item.getAmount, item.getCurrency,
        item.getProration, CustomerLineItemPeriod.getCustomerLineItemPeriod(item.getPeriod), item.getQuantity,
        SubscriptionPlan.withName(item.getPlan.getId), item.getDescription)
    }
}
case class CustomerInvoiceItem(id : String, amount : Int, currency : String,
                                proration : Boolean, period : CustomerLineItemPeriod, quantity : Int,
                                plan : SubscriptionPlan.Value, description : String)
object CustomerInvoice {
  def getCustomerInvoice(invoice: Invoice) : CustomerInvoice = {
    CustomerInvoice(invoice.getId, invoice.getSubtotal, invoice.getTotal, invoice.getAmountDue, invoice.getCreated,
      invoice.getCustomer, invoice.getDate, invoice.getPaid, invoice.getPeriodStart, invoice.getPeriodEnd,
      invoice.getCurrency, invoice.getSubscription, invoice.getLines.getData.asScala.toList.map(l => CustomerInvoiceItem.getCustomerInvoiceItem(l)))
  }
}
case class CustomerInvoice(id : String, subtotal : Int, total : Int, amountDue : Int, created : Long,
                           customer : String, date : Long, paid : Boolean, periodStart : Long, periodEnd : Long,
                           currency : String, subscription : String, lines : List[CustomerInvoiceItem])
object CustomerCard {
   def getCustomerCard(card : Card) : CustomerCard = {
    CustomerCard(Some(card.getBrand), card.getLast4, card.getExpMonth, card.getExpYear,
      card.getCvcCheck, card.getName, card.getAddressLine1, Some(card.getAddressLine2), card.getAddressCity,
      card.getAddressState, card.getAddressZip, card.getCountry)
  }
}

case class CustomerCard(brand: Option[String], number : String, expMonth : java.lang.Integer, expYear : java.lang.Integer,
                         cvc : String, name : String, address1 : String, address2 : Option[String], city :
                         String, zip : String, state : String, country : String) {

  def getMutableData : Map [String, Object] = {
    Map("address_city" -> city,
        "address_country" -> country,
        "address_line1" -> address1,
        "address_line2" -> address2,
        "address_state" -> state,
        "address_zip" -> zip,
        "exp_month" -> expMonth,
        "exp_year" ->expMonth,
        "name" -> name)
  }
}

object SubscriptionFeature extends Enumeration {
  val NAC_NUMBER_OF_VMS = Value("Number of Virtual Machines")
  val NAC_NUMBER_OF_CLUSTERS = Value("Number of Clusters")
  val NAC_NUMBER_OF_NODES = Value("Number of Cluster Nodes")
}

object SubscriptionPlan extends Enumeration {
  val NACREOUS_DEVELOPER = Value("NAC_DEVELOPER")
  val NACREOUS_BRONZE = Value("NAC_BRONZE")
  val NACREOUS_SILVER = Value("NAC_SILVER")
  val NACREOUS_GOLD = Value("NAC_GOLD")

  def isPaidAccount(plan: SubscriptionPlan.Value) : Boolean = {
    plan match {
      case SubscriptionPlan.NACREOUS_DEVELOPER => {
        false
      }
      case SubscriptionPlan.NACREOUS_BRONZE => {
        true
      }
      case SubscriptionPlan.NACREOUS_SILVER => {
        true
      }
      case SubscriptionPlan.NACREOUS_GOLD => {
        true
      }
    }
  }

  def getFeaturesForPlan(plan: SubscriptionPlan.Value) : Map[SubscriptionFeature.Value, Int] = {
    plan match {
      case SubscriptionPlan.NACREOUS_DEVELOPER => {
        Map(SubscriptionFeature.NAC_NUMBER_OF_VMS -> 2,
          SubscriptionFeature.NAC_NUMBER_OF_CLUSTERS -> 1,
          SubscriptionFeature.NAC_NUMBER_OF_NODES -> 10)
      }
      case SubscriptionPlan.NACREOUS_BRONZE => {
        Map(SubscriptionFeature.NAC_NUMBER_OF_VMS -> 5,
          SubscriptionFeature.NAC_NUMBER_OF_CLUSTERS -> 5,
          SubscriptionFeature.NAC_NUMBER_OF_NODES -> 25)
      }
      case SubscriptionPlan.NACREOUS_SILVER => {
        Map(SubscriptionFeature.NAC_NUMBER_OF_VMS -> 25,
          SubscriptionFeature.NAC_NUMBER_OF_CLUSTERS -> 255,
          SubscriptionFeature.NAC_NUMBER_OF_NODES -> 100)
      }
      case SubscriptionPlan.NACREOUS_GOLD => {
        Map(SubscriptionFeature.NAC_NUMBER_OF_VMS -> -1,
          SubscriptionFeature.NAC_NUMBER_OF_CLUSTERS -> 255,
          SubscriptionFeature.NAC_NUMBER_OF_NODES -> -1)
      }
    }
  }
}