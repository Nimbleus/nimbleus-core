package com.nimbleus.core.cloud

object CloudServiceProvider extends Enumeration {
  val AWS_EC2 = Value("Amazon EC2")
  val DIGITALOCEAN = Value("Digital Ocean")
  val AZURE = Value("Azure")
  val UNKNOWN = Value("Unknown")

  def isValidProvider(provider: String) : Boolean = {
    try {
      CloudServiceProvider.withName(provider)
      true
    }
    catch {
      case e: Throwable => {
        false
      }
    }
  }
}
