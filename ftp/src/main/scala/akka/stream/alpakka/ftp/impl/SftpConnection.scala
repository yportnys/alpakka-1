/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.ftp.impl

import java.io.File
import java.nio.charset.StandardCharsets

import akka.annotation.InternalApi
import akka.stream.alpakka.ftp.{
  FtpAuthenticationException,
  KeyFileSftpIdentity,
  RawKeySftpIdentity,
  SftpIdentity,
  SftpSettings
}
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.{AuthPassword, AuthPublickey}
import net.schmizz.sshj.userauth.password.{PasswordFinder, PasswordUtils, Resource}
import org.apache.commons.net.DefaultSocketFactory

/**
 * INTERNAL API
 */
@InternalApi
private[ftp] class SftpConnection(client: SSHClient, connectionSettings: SftpSettings)
    extends Connection[SSHClient, SftpSettings](client, connectionSettings) {

  type Handler = SFTPClient

  override def connect(): SFTPClient = {
    try {
      val conn = connectionSettings
      import conn._

      proxy.foreach(p => client.setSocketFactory(new DefaultSocketFactory(p)))

      if (!strictHostKeyChecking) {
        client.addHostKeyVerifier(new PromiscuousVerifier)
      } else {
        knownHosts.foreach(path => client.loadKnownHosts(new File(path)))
      }
      client.connect(host.getHostAddress, port)

      sftpIdentity match {
        case Some(identity) =>
          val keyAuth = authPublickey(identity)

          if (credentials.password != "") {
            val passwordAuth: AuthPassword = new AuthPassword(new PasswordFinder() {
              def reqPassword(resource: Resource[_]): Array[Char] = credentials.password.toCharArray

              def shouldRetry(resource: Resource[_]) = false
            })

            client.auth(credentials.username, passwordAuth, keyAuth)
          } else {
            client.auth(credentials.username, keyAuth)
          }
        case None =>
          if (credentials.password != "") {
            client.authPassword(credentials.username, credentials.password)
          }
      }

      client.newSFTPClient()
    } catch {
      case _: UserAuthException =>
        throw new FtpAuthenticationException(
          s"unable to login to host=[${connectionSettings.host}], port=${connectionSettings.port} ${connectionSettings.proxy
            .fold("")("proxy=" + _.toString)}"
        )
    }
  }

  private[this] def authPublickey(identity: SftpIdentity) = {
    def bats(array: Array[Byte]): String = new String(array, StandardCharsets.UTF_8)

    val passphrase =
      identity.privateKeyFilePassphrase
        .map(pass => PasswordUtils.createOneOff(bats(pass).toCharArray))
        .orNull

    identity match {
      case id: RawKeySftpIdentity =>
        new AuthPublickey(
          client.loadKeys(bats(id.privateKey), id.publicKey.map(bats).orNull, passphrase)
        )
      case id: KeyFileSftpIdentity =>
        new AuthPublickey(client.loadKeys(id.privateKey, passphrase))
    }
  }

  def disconnect(): Unit = {
    handler.close()
    if (client.isConnected)
      client.disconnect()
  }

}
