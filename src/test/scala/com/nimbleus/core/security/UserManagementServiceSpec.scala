package com.nimbleus.core.security

import java.util.Properties

import com.stormpath.sdk.application.Application
import com.stormpath.sdk.client.{ApiKey, ApiKeys, Clients}
import com.stripe.Stripe
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

class UserManagementServiceSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures {
  private val USER_USERNAME = "cstewart@nimbleus.com"
  private val USER_FIRST_NAME = "Craig"
  private val USER_LAST_NAME = "Stewart"
  private val USER_EMAIL = "cstewart@nimbleus.com"
  private val USER_INVALID_EMAIL = "testxxx@nimbleus.com"
  private val USER_PASSWORD = "Tester1234"
  private val USER_INVALID_PASSWORD = "Tester1234xx5654"
  private val AUTH_INVALID_TOKEN = "dummytoken"

  override def beforeAll(): Unit = {
  }

  override protected def afterAll(): Unit = {
  }

  val user = UserDTO(USER_USERNAME, USER_FIRST_NAME, USER_LAST_NAME, USER_EMAIL, None, None, None)

  "The user management service" should {
    "create and delete a user" in {
      val userFuture = UserManagementService.createUser(user, USER_PASSWORD)
      whenReady(userFuture) { result =>
        result should be ('right)
        val newUser = result.right.get
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
    "update user" in {
      // TODO Implement me
    }
  }
}
