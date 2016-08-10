package com.nimbleus.core.security

import com.nimbleus.core.subscription.{UserCoupon, UserDiscount, UserPlan, UserSubscription}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import scredis.Redis
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, RootJsonFormat}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

case class RedisUser(cid: String, token: String, username: String, roles: List[String],
                firstName: String, lastName: String, email: String, href: String,
                customerId: String, subscription: Option[String], digitalOceanAccessToken: Option[String],
                awsCredentials : Option[AWSCredentials])
object RedisUser {
  def fromUser(user: User) : RedisUser = {
    RedisUser(Option(user.cid).getOrElse(""), Option(user.token).getOrElse(""), Option(user.username).getOrElse(""), user.roles,
      Option(user.firstName).getOrElse(""), Option(user.lastName).getOrElse(""), Option(user.email).getOrElse(""), Option(user.href).getOrElse(""),
      Option(user.customerId).getOrElse(""), user.subscriptionId, user.digitalOceanAccessToken, user.awsCredentials)
  }
  def toUser(redisUser: RedisUser) : User = {
    User(redisUser.cid, redisUser.token, redisUser.username, redisUser.roles,
      redisUser.firstName, redisUser.lastName, redisUser.email, redisUser.href,
      redisUser.customerId, redisUser.subscription, redisUser.digitalOceanAccessToken, redisUser.awsCredentials)
  }
}

object RedisSessionStore extends SessionStore {
  val pwd = config.getString("redis-session-store-password")
  val environment = config.getString("redis-session-store-environment")
  val client = Redis(host = config.getString("redis-session-store-url"), port = config.getInt("redis-session-store-port"), passwordOpt = Some(pwd))

  object MyUserJsonProtocol extends DefaultJsonProtocol {

    implicit object DateTimeFormat extends RootJsonFormat[DateTime] {

      val formatter = ISODateTimeFormat.basicDateTimeNoMillis

      def write(obj: DateTime): JsValue = {
        JsString(formatter.print(obj))
      }

      def read(json: JsValue): DateTime = json match {
        case JsString(s) => try {
          formatter.parseDateTime(s)
        }
        catch {
          case t: Throwable => error(s)
        }
        case _ =>
          throw new DeserializationException(json.toString())
      }
    }

    implicit val AWSCredentialsFormat = jsonFormat4(AWSCredentials.apply)
    implicit val UserPlanFormat = jsonFormat8(UserPlan.apply)
    implicit val UserCouponFormat = jsonFormat10(UserCoupon.apply)
    implicit val UserDiscountFormat = jsonFormat6(UserDiscount.apply)
    implicit val UserSubscriptionFormat = jsonFormat14(UserSubscription.apply)
    implicit val UserFormat = jsonFormat12(RedisUser.apply)
  }

  import MyUserJsonProtocol._
  import spray.json._

  override def addSession(user: User): Boolean = {
    val redisUser = RedisUser.fromUser(user)
    val f = (for {
      auth <- client.auth(pwd)
      res <- client.set(environment + "-" + redisUser.token, redisUser.toJson.prettyPrint)
      if res == true
      exp <- client.expire(environment + "-" + redisUser.token, ttl)
      if exp == true
    } yield { true }).recover {
      case ex : Exception => { false }
    }
    Await.result[Boolean](f, 5 seconds)
  }
  override def getSession(token: String): Option[User] = {
    val f = (for {
      auth <- client.auth(pwd)
      userRes <- client.get[String](environment + "-" + token)
      if userRes.isDefined
      exp <- client.expire(environment + "-" + token, ttl)
      if exp == true
    } yield {
      val jsonAst = userRes.get.parseJson
      val user = jsonAst.convertTo[RedisUser]
      Some(RedisUser.toUser(user))
    }).recover {
      case ex : Exception => { None }
    }
    Await.result[Option[User]](f, 5 seconds)
  }

  override def expireSession(user: User): Boolean = {
    expireSession(user.token)
  }

  override def expireSession(token: String): Boolean = {
    val f = (for {
      auth <- client.auth(pwd)
      res <- client.del(environment + "-" + token)
      if res == 1
    } yield { true }).recover {
      case ex : Exception => { false }
    }
    Await.result[Boolean](f, 5 seconds)
  }
}
