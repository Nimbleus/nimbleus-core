package com.nimbleus.core.subscription

trait SubscriptionRoute extends BaseSubscriptionRoute {
  // TODO define the base route

  val subscriptionRoute = {

  }

  private def createSubscription(customerId: String, planId: String) : Unit = {}
  private def changeSubscription(customerId: String, planId: String) : Unit = {}
  private def cancelSubscription(customerId: String) : Unit = {}
}
