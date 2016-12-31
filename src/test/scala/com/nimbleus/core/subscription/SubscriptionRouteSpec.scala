package com.nimbleus.core.subscription

import java.util
import java.util.Properties

import com.nimbleus.core.security.{UserDTO, UserManagementService}
import com.stormpath.sdk.account.{AccountCriteria, AccountList, Accounts}
import com.stormpath.sdk.application.Application
import com.stormpath.sdk.client.{ApiKey, ApiKeys, Clients}
import com.stripe.Stripe
import com.stripe.exception.InvalidRequestException
import com.stripe.model.{Card, Customer, Token}
import com.typesafe.config.{ConfigException, ConfigFactory}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpecLike}

class SubscriptionRouteSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with ScalaFutures
         with NimbleusSubscription {
  private val USER_USERNAME = "cstewart@nimbleus.com"
  private val USER_FIRST_NAME = "Craig"
  private val USER_LAST_NAME = "Stewart"
  private val USER_EMAIL = "subscriptionservice@nimbleus.com"
  private val USER_INVALID_EMAIL = "testxxx@nimbleus.com"
  private val USER_PASSWORD = "Tester1234"
  private val USER_INVALID_PASSWORD = "Tester1234xx5654"
  private val AUTH_INVALID_TOKEN = "dummytoken"

  val config = ConfigFactory.load

  // define constructs for remote user management services
  private val CLIENT_ID = "cid"
  private val CUSTOMER_ID = "customer_id"
  private var properties: Properties = new Properties()
  properties.setProperty("apiKey.id", config.getString("security-api-key"))
  properties.setProperty("apiKey.secret", config.getString("security-api-secret"))
  private val apiKey: ApiKey = ApiKeys.builder().setProperties(properties).build()
  private val client: com.stormpath.sdk.client.Client = Clients.builder().setApiKey(apiKey).build()
  val applicationHref = config.getString("stormpath-application-href")
  val directoryHref = config.getString("stormpath-directory-href")
  val application: Application = client.getResource(applicationHref, classOf[Application])

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

  override def beforeAll(): Unit = {
  }

  override protected def afterAll(): Unit = {
  }

  override def beforeEach(): Unit = {
    deleteTestUser(USER_EMAIL)
  }

  val user = UserDTO(USER_USERNAME, USER_FIRST_NAME, USER_LAST_NAME, USER_EMAIL, None, None, None)

  private def deleteTestUser(email: String): Unit = {
    try {

      val criteria: AccountCriteria = Accounts.where(Accounts.email().eqIgnoreCase(email))
      val accounts: AccountList = application.getAccounts(criteria)
      if (accounts.iterator().hasNext) {
        // the test account exists so delete it
        accounts.iterator().next().delete()
      }
    }
    catch {
      case e: Throwable => {
        println(e.getMessage)
      }
    }
  }

  "The user subscription service" should {
    "create user and add credit card" in {
      val userFuture = UserManagementService.createUser(user, USER_PASSWORD)
      whenReady(userFuture) { result =>
        result should be ('right)
        val newUser = result.right.get
        val defaultCardParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultCardParams.put("number", "4242424242424242")
        defaultCardParams.put("exp_month", 12.asInstanceOf[Object])
        defaultCardParams.put("exp_year", 2020.asInstanceOf[Object])
        defaultCardParams.put("cvc", "123");
        defaultCardParams.put("name", "J Bindings Cardholder")
        defaultCardParams.put("address_line1", "140 2nd Street")
        defaultCardParams.put("address_line2", "4th Floor")
        defaultCardParams.put("address_city", "San Francisco")
        defaultCardParams.put("address_zip", "94105")
        defaultCardParams.put("address_state", "CA")
        defaultCardParams.put("address_country", "USA")

        val defaultTokenParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultTokenParams.put("card", defaultCardParams)

        val cardToken: Token = Token.create(defaultTokenParams)
        val addCardResult = addCreditCard(newUser.customerId, cardToken.getId)

        addCardResult should be ('Right)
        addCardResult.right.get.length should be > 0

        val customer: Customer = Customer.retrieve(newUser.customerId)
        val card: Card = customer.getSources().retrieve(addCardResult.right.get).asInstanceOf[Card]

        val deleteFuture =  UserManagementService.deleteUser(newUser)
        whenReady(deleteFuture) { dr =>
          dr should be (true)
          val foundFuture = UserManagementService.accountExists(newUser)
          whenReady(foundFuture) { fr =>
            fr should be (false)
          }
        }
      }
    }
    "create user, add card, and remove card" in {
      val userFuture = UserManagementService.createUser(user, USER_PASSWORD)
      whenReady(userFuture) { result =>
        result should be ('right)
        val newUser = result.right.get
        val defaultCardParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultCardParams.put("number", "4242424242424242")
        defaultCardParams.put("exp_month", 12.asInstanceOf[Object])
        defaultCardParams.put("exp_year", 2020.asInstanceOf[Object])
        defaultCardParams.put("cvc", "123");
        defaultCardParams.put("name", "J Bindings Cardholder")
        defaultCardParams.put("address_line1", "140 2nd Street")
        defaultCardParams.put("address_line2", "4th Floor")
        defaultCardParams.put("address_city", "San Francisco")
        defaultCardParams.put("address_zip", "94105")
        defaultCardParams.put("address_state", "CA")
        defaultCardParams.put("address_country", "USA")

        val defaultTokenParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultTokenParams.put("card", defaultCardParams)

        val cardToken: Token = Token.create(defaultTokenParams)
        val addCardResult = addCreditCard(newUser.customerId, cardToken.getId)

        addCardResult should be ('Right)
        addCardResult.right.get.length should be > 0

        val customer: Customer = Customer.retrieve(newUser.customerId)
        val card: Card = customer.getSources().retrieve(addCardResult.right.get).asInstanceOf[Card]

        // delete the card
        val removeCardResult = removeCreditCard(newUser.customerId, card.getId)
        removeCardResult should be ('Right)
        removeCardResult.right.get should be (true)

        val thrown = intercept[InvalidRequestException] {
          customer.getSources().retrieve(card.getId).asInstanceOf[Card]
        }
        assert(thrown.getStatusCode === 404)

        val deleteFuture =  UserManagementService.deleteUser(newUser)
        whenReady(deleteFuture) { dr =>
          dr should be (true)
          val foundFuture = UserManagementService.accountExists(newUser)
          whenReady(foundFuture) { fr =>
            fr should be (false)
          }
        }
      }
    }
    "create user then add, update, and remove card" in {
      val userFuture = UserManagementService.createUser(user, USER_PASSWORD)
      whenReady(userFuture) { result =>
        result should be ('right)
        val newUser = result.right.get
        val defaultCardParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultCardParams.put("number", "4242424242424242")
        defaultCardParams.put("exp_month", 12.asInstanceOf[Object])
        defaultCardParams.put("exp_year", 2020.asInstanceOf[Object])
        defaultCardParams.put("cvc", "123");
        defaultCardParams.put("name", "J Bindings Cardholder")
        defaultCardParams.put("address_line1", "140 2nd Street")
        defaultCardParams.put("address_line2", "4th Floor")
        defaultCardParams.put("address_city", "San Francisco")
        defaultCardParams.put("address_zip", "94105")
        defaultCardParams.put("address_state", "CA")
        defaultCardParams.put("address_country", "USA")

        val defaultTokenParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultTokenParams.put("card", defaultCardParams)

        val cardToken: Token = Token.create(defaultTokenParams)
        val addCardResult = addCreditCard(newUser.customerId, cardToken.getId)

        addCardResult should be ('Right)
        addCardResult.right.get.length should be > 0

        val customer: Customer = Customer.retrieve(newUser.customerId)
        val card: Card = customer.getSources().retrieve(addCardResult.right.get).asInstanceOf[Card]

        // update the card
        val updateParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        updateParams.put("address_city", "Paris")
        updateParams.put("address_country", "FR")
        updateParams.put("address_line1", "100 Test Road")
        updateParams.put("address_line2", "Apartment C")
        updateParams.put("address_state", "Leonne")
        updateParams.put("address_zip", "19422")
        updateParams.put("exp_month", 10.asInstanceOf[Object])
        updateParams.put("exp_year", 2030.asInstanceOf[Object])
        updateParams.put("name", "Craig Stewart")

        val updateCardResult = updateCreditCard(newUser.customerId, card.getId, updateParams)

        updateCardResult should be ('Right)
        val updatedCard = updateCardResult.right.get
        // asserts on the result
        updatedCard.address1 should be (Some("100 Test Road"))
        updatedCard.address2 should be (Some("Apartment C"))
        updatedCard.city should be (Some("Paris"))
        updatedCard.country should be (Some("FR"))
        updatedCard.state should be (Some("Leonne"))
        updatedCard.zip should be (Some("19422"))
        updatedCard.expMonth should be (Some(10))
        updatedCard.expYear should be (Some(2030))
        updatedCard.name should be (Some("Craig Stewart"))

        // delete the card
        val removeCardResult = removeCreditCard(newUser.customerId, card.getId)
        removeCardResult should be ('Right)
        removeCardResult.right.get should be (true)

        val thrown = intercept[InvalidRequestException] {
          customer.getSources().retrieve(card.getId).asInstanceOf[Card]
        }
        assert(thrown.getStatusCode === 404)

        val deleteFuture =  UserManagementService.deleteUser(newUser)
        whenReady(deleteFuture) { dr =>
          dr should be (true)
          val foundFuture = UserManagementService.accountExists(newUser)
          whenReady(foundFuture) { fr =>
            fr should be (false)
          }
        }
      }
    }
    "create user then add, get, and remove card" in {
      val userFuture = UserManagementService.createUser(user, USER_PASSWORD)
      whenReady(userFuture) { result =>
        result should be ('right)
        val newUser = result.right.get
        val defaultCardParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultCardParams.put("number", "4242424242424242")
        defaultCardParams.put("exp_month", 12.asInstanceOf[Object])
        defaultCardParams.put("exp_year", 2020.asInstanceOf[Object])
        defaultCardParams.put("cvc", "123");
        defaultCardParams.put("name", "J Bindings Cardholder")
        defaultCardParams.put("address_line1", "140 2nd Street")
        defaultCardParams.put("address_line2", "4th Floor")
        defaultCardParams.put("address_city", "San Francisco")
        defaultCardParams.put("address_zip", "94105")
        defaultCardParams.put("address_state", "CA")
        defaultCardParams.put("address_country", "USA")

        val defaultTokenParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultTokenParams.put("card", defaultCardParams)

        val cardToken: Token = Token.create(defaultTokenParams)
        val addCardResult = addCreditCard(newUser.customerId, cardToken.getId)
        addCardResult should be ('Right)
        addCardResult.right.get.length should be > 0

        val findResult = getCreditCard(newUser.customerId, addCardResult.right.get)
        findResult should be ('Right)
        val foundCard = findResult.right.get

        //foundCard.number should endWith ("4242")
        foundCard.name should be (Some("J Bindings Cardholder"))
        foundCard.expMonth should be (Some(12))
        foundCard.expYear should be (Some(2020))

        // delete the card
        val removeCardResult = removeCreditCard(newUser.customerId, foundCard.id)
        removeCardResult should be ('Right)
        removeCardResult.right.get should be (true)

        val deleteFuture =  UserManagementService.deleteUser(newUser)
        whenReady(deleteFuture) { dr =>
          dr should be (true)
          val foundFuture = UserManagementService.accountExists(newUser)
          whenReady(foundFuture) { fr =>
            fr should be (false)
          }
        }
      }
    }
    "create user then add 2 cards then list and remove cards remove cards" in {
      val userFuture = UserManagementService.createUser(user, USER_PASSWORD)
      whenReady(userFuture) { result =>
        result should be ('right)
        val newUser = result.right.get
        val defaultCardParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultCardParams.put("number", "4242424242424242")
        defaultCardParams.put("exp_month", 12.asInstanceOf[Object])
        defaultCardParams.put("exp_year", 2020.asInstanceOf[Object])
        defaultCardParams.put("cvc", "123");
        defaultCardParams.put("name", "J Bindings Cardholder")
        defaultCardParams.put("address_line1", "140 2nd Street")
        defaultCardParams.put("address_line2", "4th Floor")
        defaultCardParams.put("address_city", "San Francisco")
        defaultCardParams.put("address_zip", "94105")
        defaultCardParams.put("address_state", "CA")
        defaultCardParams.put("address_country", "USA")
        val defaultTokenParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultTokenParams.put("card", defaultCardParams)
        val cardToken: Token = Token.create(defaultTokenParams)
        val addCardResult = addCreditCard(newUser.customerId, cardToken.getId)
        addCardResult should be ('Right)
        addCardResult.right.get.length should be > 0

        val defaultCardParams2: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultCardParams2.put("number", "5555555555554444")
        defaultCardParams2.put("exp_month", 6.asInstanceOf[Object])
        defaultCardParams2.put("exp_year", 2019.asInstanceOf[Object])
        defaultCardParams2.put("cvc", "123");
        defaultCardParams2.put("name", "J Bindings Cardholder")
        defaultCardParams2.put("address_line1", "140 2nd Street")
        defaultCardParams2.put("address_line2", "4th Floor")
        defaultCardParams2.put("address_city", "San Francisco")
        defaultCardParams2.put("address_zip", "94105")
        defaultCardParams2.put("address_state", "CA")
        defaultCardParams2.put("address_country", "USA")
        val defaultTokenParams2: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultTokenParams2.put("card", defaultCardParams2)
        val cardToken2: Token = Token.create(defaultTokenParams2)
        val addCardResult2 = addCreditCard(newUser.customerId, cardToken2.getId)
        addCardResult2 should be ('Right)
        addCardResult2.right.get.length should be > 0

        val findResult = getCreditCards(newUser.customerId)
        findResult should be ('Right)
        val foundCards = findResult.right.get
        foundCards.length should be (2)

        for (card <- foundCards) {
          // delete all the cards
          val removeCardResult = removeCreditCard(newUser.customerId, card.id)
          removeCardResult should be ('Right)
          removeCardResult.right.get should be (true)
        }

        val deleteFuture =  UserManagementService.deleteUser(newUser)
        whenReady(deleteFuture) { dr =>
          dr should be (true)
          val foundFuture = UserManagementService.accountExists(newUser)
          whenReady(foundFuture) { fr =>
            fr should be (false)
          }
        }
      }
    }

    "change subscription" in {
      val userFuture = UserManagementService.createUser(user, USER_PASSWORD)
      whenReady(userFuture) { result =>
        result should be ('right)
        val newUser = result.right.get
        val defaultCardParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultCardParams.put("number", "4242424242424242")
        defaultCardParams.put("exp_month", 12.asInstanceOf[Object])
        defaultCardParams.put("exp_year", 2020.asInstanceOf[Object])
        defaultCardParams.put("cvc", "123");
        defaultCardParams.put("name", "J Bindings Cardholder")
        defaultCardParams.put("address_line1", "140 2nd Street")
        defaultCardParams.put("address_line2", "4th Floor")
        defaultCardParams.put("address_city", "San Francisco")
        defaultCardParams.put("address_zip", "94105")
        defaultCardParams.put("address_state", "CA")
        defaultCardParams.put("address_country", "USA")

        val defaultTokenParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultTokenParams.put("card", defaultCardParams)

        val cardToken: Token = Token.create(defaultTokenParams)
        val addCardResult = addCreditCard(newUser.customerId, cardToken.getId)

        addCardResult should be ('Right)
        addCardResult.right.get.length should be > 0

        val customer: Customer = Customer.retrieve(newUser.customerId)
        val card: Card = customer.getSources().retrieve(addCardResult.right.get).asInstanceOf[Card]

        // create subscription
        val subscriptionResult = changePlan(newUser.customerId, Some("NAC_GOLD"))
        subscriptionResult should be ('Right)
        subscriptionResult.right.get.get.length should be > 0
        val subscriptionId = subscriptionResult.right.get.get

        // delete subscription
        val cancelSubscriptionResult = changePlan(newUser.customerId, None)
        cancelSubscriptionResult should be ('Right)
        cancelSubscriptionResult.right.get should be (None)

        // delete the card
        val removeCardResult = removeCreditCard(newUser.customerId, card.getId)
        removeCardResult should be ('Right)
        removeCardResult.right.get should be (true)

        val thrown = intercept[InvalidRequestException] {
          customer.getSources().retrieve(card.getId).asInstanceOf[Card]
        }
        assert(thrown.getStatusCode === 404)

        val deleteFuture =  UserManagementService.deleteUser(newUser)
        whenReady(deleteFuture) { dr =>
          dr should be (true)
          val foundFuture = UserManagementService.accountExists(newUser)
          whenReady(foundFuture) { fr =>
            fr should be (false)
          }
        }
      }
    }

    "get user subscription" in {
      val userFuture = UserManagementService.createUser(user, USER_PASSWORD)
      whenReady(userFuture) { result =>
        result should be ('right)
        val newUser = result.right.get
        val defaultCardParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultCardParams.put("number", "4242424242424242")
        defaultCardParams.put("exp_month", 12.asInstanceOf[Object])
        defaultCardParams.put("exp_year", 2020.asInstanceOf[Object])
        defaultCardParams.put("cvc", "123");
        defaultCardParams.put("name", "J Bindings Cardholder")
        defaultCardParams.put("address_line1", "140 2nd Street")
        defaultCardParams.put("address_line2", "4th Floor")
        defaultCardParams.put("address_city", "San Francisco")
        defaultCardParams.put("address_zip", "94105")
        defaultCardParams.put("address_state", "CA")
        defaultCardParams.put("address_country", "USA")

        val defaultTokenParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultTokenParams.put("card", defaultCardParams)

        val cardToken: Token = Token.create(defaultTokenParams)
        val addCardResult = addCreditCard(newUser.customerId, cardToken.getId)

        addCardResult should be ('Right)
        addCardResult.right.get.length should be > 0

        val customer: Customer = Customer.retrieve(newUser.customerId)
        val card: Card = customer.getSources().retrieve(addCardResult.right.get).asInstanceOf[Card]

        // create subscription
        val subscriptionResult = changePlan(newUser.customerId, Some("NAC_GOLD"))
        subscriptionResult should be ('Right)
        subscriptionResult.right.get.get.length should be > 0
        val subscriptionId = subscriptionResult.right.get.get

        // get the subscription
        val subscription = getSubscriptionPlan(newUser.customerId)
        subscription should be ('defined)
        subscription.get.id should be (subscriptionId)

        // delete subscription
        val cancelSubscriptionResult = changePlan(newUser.customerId, None)
        cancelSubscriptionResult should be ('Right)
        cancelSubscriptionResult.right.get should be (None)

        // delete the card
        val removeCardResult = removeCreditCard(newUser.customerId, card.getId)
        removeCardResult should be ('Right)
        removeCardResult.right.get should be (true)

        val thrown = intercept[InvalidRequestException] {
          customer.getSources().retrieve(card.getId).asInstanceOf[Card]
        }
        assert(thrown.getStatusCode === 404)

        val deleteFuture =  UserManagementService.deleteUser(newUser)
        whenReady(deleteFuture) { dr =>
          dr should be (true)
          val foundFuture = UserManagementService.accountExists(newUser)
          whenReady(foundFuture) { fr =>
            fr should be (false)
          }
        }
      }
    }

    "list invoices" in {
      val userFuture = UserManagementService.createUser(user, USER_PASSWORD)
      whenReady(userFuture) { result =>
        result should be ('right)
        val newUser = result.right.get
        val defaultCardParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultCardParams.put("number", "4242424242424242")
        defaultCardParams.put("exp_month", 12.asInstanceOf[Object])
        defaultCardParams.put("exp_year", 2020.asInstanceOf[Object])
        defaultCardParams.put("cvc", "123");
        defaultCardParams.put("name", "J Bindings Cardholder")
        defaultCardParams.put("address_line1", "140 2nd Street")
        defaultCardParams.put("address_line2", "4th Floor")
        defaultCardParams.put("address_city", "San Francisco")
        defaultCardParams.put("address_zip", "94105")
        defaultCardParams.put("address_state", "CA")
        defaultCardParams.put("address_country", "USA")

        val defaultTokenParams: util.Map[String, Object] = new util.HashMap[String, Object]()
        defaultTokenParams.put("card", defaultCardParams)

        val cardToken: Token = Token.create(defaultTokenParams)
        val addCardResult = addCreditCard(newUser.customerId, cardToken.getId)

        addCardResult should be ('Right)
        addCardResult.right.get.length should be > 0

        val customer: Customer = Customer.retrieve(newUser.customerId)
        val card: Card = customer.getSources().retrieve(addCardResult.right.get).asInstanceOf[Card]

        // create subscription
        val subscriptionResult = changePlan(newUser.customerId, Some("NAC_GOLD"))
        subscriptionResult should be ('Right)
        subscriptionResult.right.get.get.length should be > 0
        val subscriptionId = subscriptionResult.right.get.get

        // get the invoice
        val getInvoicesResult = getInvoices(newUser.customerId, 1)
        getInvoicesResult should be ('Right)
        getInvoicesResult.right.get.length should equal(1)

        // delete subscription
        val cancelSubscriptionResult = changePlan(newUser.customerId, None)
        cancelSubscriptionResult should be ('Right)
        cancelSubscriptionResult.right.get should be (None)

        // delete the card
        val removeCardResult = removeCreditCard(newUser.customerId, card.getId)
        removeCardResult should be ('Right)
        removeCardResult.right.get should be (true)

        val thrown = intercept[InvalidRequestException] {
          customer.getSources().retrieve(card.getId).asInstanceOf[Card]
        }
        assert(thrown.getStatusCode === 404)

        val deleteFuture =  UserManagementService.deleteUser(newUser)
        whenReady(deleteFuture) { dr =>
          dr should be (true)
          val foundFuture = UserManagementService.accountExists(newUser)
          whenReady(foundFuture) { fr =>
            fr should be (false)
          }
        }
      }
    }

  }
}
