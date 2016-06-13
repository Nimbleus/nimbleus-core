package com.nimbleus.core.spray.security

import java.io.FileInputStream
import java.security.{ SecureRandom, KeyStore }
import javax.net.ssl.{ SSLContext, TrustManagerFactory, KeyManagerFactory }
import akka.actor.ActorSystem
import akka.http.scaladsl.{ ConnectionContext, HttpsConnectionContext, Http }
import com.nimbleus.core.security.SessionStore
import com.typesafe.config.{ConfigException, ConfigFactory}

// for SSL support (if enabled in application.conf)
trait NimbleusSslConfiguration {
  protected def _system: ActorSystem
  implicit val system = _system

  private def getSafeString(key: String) : Option[String] = {
    val config = ConfigFactory.load
    val s = try {
      Some(config.getString(key))
    }
    catch {
      case e : ConfigException.Missing  => { None }
    }
    s
  }

  implicit def httpsConnectionContext: HttpsConnectionContext = {
    // Manual HTTPS configuration
    val password = getSafeString("key-store-password").getOrElse("")
    val keyStoreResource = getSafeString("key-store-resource").getOrElse("")
    val keyStoreType = getSafeString("key-store-type").getOrElse("jks")
    val keyManagerFactoryType = getSafeString("key-manager-type").getOrElse("SunX509")
    val sslProtocol = getSafeString("ssl-protocol").getOrElse("TLS")

    val keyStore: KeyStore = KeyStore.getInstance(keyStoreType)
    keyStore.load(new FileInputStream(keyStoreResource), password.toCharArray)

    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance(keyManagerFactoryType)
    keyManagerFactory.init(keyStore, password.toCharArray)

    val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance(keyManagerFactoryType)
    trustManagerFactory.init(keyStore)

    val sslContext: SSLContext = SSLContext.getInstance(sslProtocol)
    sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
    val https: HttpsConnectionContext = ConnectionContext.https(sslContext)
    https
  }

  // sets default context to HTTPS – all Http() bound servers for this ActorSystem will use HTTPS from now on
  // this can be done in main app
  // we may want to have this set by the app
  Http().setDefaultServerHttpContext(httpsConnectionContext)
}