package com.nimbleus.core.security

import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.nimbleus.core.spray.security.CorsDirective
import org.scalatest.{Matchers, WordSpecLike}
import akka.http.scaladsl.server.Directives._
//import com.nimbleus.core.security.AuthenticationDirective.authenticateUser
import scala.concurrent.Future

class SecuritySpec extends WordSpecLike with Matchers with ScalatestRouteTest with UserAuthentication with CorsDirective {

  val sessionStore = RedisSessionStore

  // define CORS support
  override val corsAllowOrigins: List[String] = List("*")
  override val corsAllowedHeaders: List[String] = List("Authorization", "AuthToken", "Session", "Origin", "X-Requested-With", "Content-Type", "Accept", "Accept-Encoding", "Accept-Language", "Host", "Referer", "User-Agent")
  override val corsAllowCredentials: Boolean = true
  override val optionsCorsHeaders: List[HttpHeader] = List[HttpHeader](
    `Access-Control-Allow-Headers`(corsAllowedHeaders.mkString(", ")),
    `Access-Control-Max-Age`(60 * 60 * 24 * 20), // cache pre-flight response for 20 days
    `Access-Control-Allow-Credentials`(corsAllowCredentials)
  )

  "The security service" should {
    "custom authentication" in {
      val route = cors {
        extractCredentials { creds =>
          authenticateUser(creds) { user =>
            complete {
              creds match {
                case Some(c) => "Credentials: " + c
                case _ => "No credentials"
              }
            }
          }
        }
      }

      // tests:
      val johnsCred = BasicHttpCredentials("cstewart@nimbleus.com", "1992ZXstewy")
      Get("/") ~> addCredentials(johnsCred) ~> route ~> check {
        val test = responseAs[String]
        responseAs[String] shouldEqual "Credentials: Basic Y3N0ZXdhcnRAbmltYmxldXMuY29tOjE5OTJaWHN0ZXd5"
      }

    }
    "extract credentials" in {
      val route =
        extractCredentials { creds =>
          complete {
            creds match {
              case Some(c) => "Credentials: " + c
              case _       => "No credentials"
            }
          }
        }

      // tests:
      val johnsCred = BasicHttpCredentials("John", "p4ssw0rd")
      Get("/") ~> addCredentials(johnsCred) ~> route ~> check {
        responseAs[String] shouldEqual "Credentials: Basic Sm9objpwNHNzdzByZA=="
      }
    }
  }
}