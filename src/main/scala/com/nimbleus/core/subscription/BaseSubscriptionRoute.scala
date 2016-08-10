package com.nimbleus.core.subscription

import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import com.nimbleus.core.security.UserAuthentication

import scala.concurrent.duration._

trait BaseSubscriptionRoute extends UserAuthentication  {
  protected def mockMode: Boolean
  protected val ASK_TIMEOUT = 3 seconds
  implicit val timeout = Timeout(60.seconds)

  val AUTH_TOKEN_HEADER = "AuthToken"

  /**
    *  Ignores any method except and Options request and completes the request with a response containing Options.
    */
  def optionDirective = {
    method(HttpMethods.OPTIONS) {
      complete("Options")
    }
  }

  def processAck(condition: Boolean) : Boolean = {
    if (mockMode) { true } else { condition }
  }

}
