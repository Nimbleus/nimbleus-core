package com.nimbleus.core.util

import com.typesafe.config.{ConfigFactory, ConfigException}

object StringUtil {
  val config = ConfigFactory.load
  def getSafeConfigString(key: String, default: Option[String]) : String = {
    val s = try {
      config.getString(key)
    }
    catch {
      case e : ConfigException.Missing  => { default.getOrElse("") }
    }
    s
  }
}
