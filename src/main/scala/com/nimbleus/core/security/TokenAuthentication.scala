package com.nimbleus.core.security

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenge}
import akka.http.scaladsl.server.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import akka.http.scaladsl.server._


trait TokenAuthentication extends Directives {
  protected def sessionStore: SessionStore
  val challenge = HttpChallenge(scheme = "Token", realm = "Nimbleus", params = Map.empty)

  def authenticateToken(token: String): Directive1[User] = {
    sessionStore.getSession(token) match {
      case Some(user) => {
        provide(user)
      }
      case None => {
        reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
      }
    }
  }
}
