package com.nimbleus.core.subscription

import akka.http.scaladsl.server.RejectionHandler
import com.nimbleus.core.security.SessionStore
import com.nimbleus.core.spray.security.CorsDirective

trait CreditCardRoute extends BaseSubscriptionRoute with CorsDirective {
  protected def sessionStore: SessionStore
  implicit def myRejectionHandler: RejectionHandler

  val creditCardRoute = cors {
    handleRejections(myRejectionHandler) {
      path("TEST") {
        get {
          headerValueByName(AUTH_TOKEN_HEADER) { token =>
            complete {
              "OK"
            }
          }
        } ~ optionDirective
      }
    }
  }

  private def addCard : Unit = { }
  private def updateCard : Unit = { }
  private def removeCard : Unit = { }
  private def setDefaultCard : Unit = { }
  private def getCards : Unit = { }
}
