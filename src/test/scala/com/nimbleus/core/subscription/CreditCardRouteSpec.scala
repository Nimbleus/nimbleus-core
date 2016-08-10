package com.nimbleus.core.subscription

import java.util.Properties

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpHeader, HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, `Access-Control-Allow-Credentials`, `Access-Control-Allow-Headers`, `Access-Control-Max-Age`}
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.nimbleus.core.security.{RedisSessionStore, User, UserDTO}
import com.stormpath.sdk.account.{Account, AccountCriteria, AccountList, Accounts}
import com.stormpath.sdk.application.Application
import com.stormpath.sdk.client.{ApiKey, ApiKeys, Clients}
import com.stormpath.sdk.directory.{CustomData, Directory}
import com.stripe.Stripe
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import reactivemongo.bson.BSONObjectID

class CreditCardRouteSpec extends WordSpecLike with ScalatestRouteTest with CreditCardRoute with Matchers with BeforeAndAfterAll {
  def actorRefFactory = system // connect the DSL to the test ActorSystem

  override val corsAllowOrigins: List[String] = List("*")
  override val corsAllowedHeaders: List[String] = List("Authorization", "AuthToken", "Session", "Origin", "X-Requested-With", "Content-Type", "Accept", "Accept-Encoding", "Accept-Language", "Host", "Referer", "User-Agent")
  override val corsAllowCredentials: Boolean = true
  override val optionsCorsHeaders: List[HttpHeader] = List[HttpHeader](
    `Access-Control-Allow-Headers`(corsAllowedHeaders.mkString(", ")),
    `Access-Control-Max-Age`(60 * 60 * 24 * 20), // cache pre-flight response for 20 days
    `Access-Control-Allow-Credentials`(corsAllowCredentials))

  val mockMode = true
  private val CLIENT_ID = "cid"
  private val CUSTOMER_ID = "customer_id"
  private var properties: Properties = new Properties()
  val config = ConfigFactory.load
  properties.setProperty("apiKey.id", config.getString("security-api-key"))
  properties.setProperty("apiKey.secret", config.getString("security-api-secret"))
  private val apiKey: ApiKey = ApiKeys.builder().setProperties(properties).build()
  private val client: com.stormpath.sdk.client.Client  = Clients.builder().setApiKey(apiKey).build()
  val applicationHref = config.getString("stormpath-application-href")
  val directoryHref = config.getString("stormpath-directory-href")
  val application: Application = client.getResource(applicationHref, classOf[Application])
  Stripe.apiKey = config.getString("stripe-api-key")

  val sessionStore = RedisSessionStore

  implicit def myRejectionHandler =
    RejectionHandler.newBuilder()
      .handle { case MissingCookieRejection(cookieName) =>
        complete(HttpResponse(BadRequest, entity = "Cookies are required"))
      }
      .handle { case AuthenticationFailedRejection(cause, challengeHeaders) =>
        complete((Unauthorized, cause.toString))
      }
      .handle { case ValidationRejection(msg, _) ⇒
        complete((InternalServerError, "Invalid: " + msg))
      }
      .handleAll[MethodRejection] { methodRejections ⇒
      val names = methodRejections.map(_.supported.name)
      complete((MethodNotAllowed, s"Can't do that! Supported: ${names mkString " or "}!"))
    }.handleNotFound { complete((NotFound, "Not here!")) }
      .result()


  // create a variable to hold an optional test user account
  var user: Option[User] = None
  private val USER_USERNAME = "cstewart@nimbleus.com"
  private val USER_FIRST_NAME = "Craig"
  private val USER_LAST_NAME = "Stewart"
  private val USER_EMAIL = "cstewart@nimbleus.com"
  private val USER_INVALID_EMAIL = "testxxx@nimbleus.com"
  private val USER_PASSWORD = "Tester1234"
  private val USER_INVALID_PASSWORD = "Tester1234xx5654"
  private val AUTH_INVALID_TOKEN = "dummytoken"

  override def beforeAll(): Unit = {
    //user = createTestUser(UserDTO(USER_USERNAME, USER_FIRST_NAME, USER_LAST_NAME, USER_EMAIL, None, None, None), USER_PASSWORD)
  }

  override protected def afterAll(): Unit = {
    //cleanUp()
    //if(user.isDefined) {deleteTestUser(user.get)}
  }

  private def createTestUser(user: UserDTO, password: String) : Option[User] = {
    try {
      val criteria: AccountCriteria = Accounts.where(Accounts.email().eqIgnoreCase(user.email))
      val accounts: AccountList = application.getAccounts(criteria)
      if (accounts.iterator().hasNext) {
        // this account exists so error out
        accounts.iterator().next().delete
      }
      val account: Account = client.instantiate(classOf[Account])
      account.setEmail(user.email)
      account.setGivenName(user.firstName)
      account.setPassword(password)
      account.setSurname(user.lastName)
      account.setUsername(user.username)
      val directory: Directory = client.getResource(directoryHref, classOf[Directory])
      directory.createAccount(account)
      println("created test account")
      val customData: CustomData = account.getCustomData()
      val newCid = BSONObjectID.generate.stringify
      customData.put(CLIENT_ID, newCid)
      // create a subscription for this new user
      customData.save
      // TODO Fix me
      //Some(User(newCid, User.getToken, account.getUsername, List.empty, account.getGivenName,
      //  account.getSurname, account.getEmail, account.getHref, "", SubscriptionPlan.NACREOUS_DEVELOPER, None, None))
      Some(User(newCid, User.getToken, account.getUsername, List.empty, account.getGivenName,
        account.getSurname, account.getEmail, account.getHref, "", None, None, None))
    }
    catch {
      case e: Throwable => {
        println(e.getMessage)
        None
      }
    }
  }

  private def deleteTestUser(user: User) : Unit = {
    try {
      val account: Account = client.getResource(user.href, classOf[Account])
      account.delete
    }
    catch {
      case e: Throwable => {println(e.getMessage)}
    }
  }

  private val ROUTE_LOGIN = "/v1/security/login"
  private val ROUTE_LOGOUT = "/v1/security/logout"

  val credentials = BasicHttpCredentials("cstewart@nimbleus.com", "Tester1234")

  "The credit card service" should {
    "add a credit card" in {

    }
  }
}
