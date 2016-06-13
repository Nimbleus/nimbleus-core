package com.nimbleus.core.security

import java.io.ByteArrayOutputStream
import com.jcraft.jsch.{KeyPair, JSch}

object Ssh {
  def generateSshKeys : Tuple2[String, String] = {
    val jsch: JSch  = new JSch()
    val kpair: KeyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA)
    val os : ByteArrayOutputStream  = new ByteArrayOutputStream
    kpair.writePrivateKey(os)
    val priv = new String(os.toByteArray(),"UTF-8")
    val os2 : ByteArrayOutputStream  = new ByteArrayOutputStream
    kpair.writePublicKey(os2, "cstewart@nimbleus.com")
    val pub = new String(os2.toByteArray(),"UTF-8")
    (pub, priv)
  }
}
