/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.ftp.impl

import akka.annotation.InternalApi
import akka.stream.alpakka.ftp.{FtpAuthenticationException, FtpSettings}
import org.apache.commons.net.ftp.{FTP, FTPClient}

/**
 * INTERNAL API
 */
@InternalApi
private[ftp] class FtpConnection(client: FTPClient, connectionSettings: FtpSettings)
    extends Connection[FTPClient, FtpSettings](client, connectionSettings) {

  override type Handler = FTPClient

  override def connect(): FTPClient = {
    val conn = connectionSettings
    import conn._

    proxy.foreach(client.setProxy)

    try {
      client.connect(host, port)
    } catch {
      case e: java.net.ConnectException =>
        throw new java.net.ConnectException(
          e.getMessage + s" host=[${host}], port=${port} ${proxy
            .map("proxy=" + _.toString)
            .getOrElse("")}"
        )
    }

    configureConnection(client)

    client.login(
      credentials.username,
      credentials.password
    )
    if (client.getReplyCode == 530) {
      throw new FtpAuthenticationException(
        s"unable to login to host=[${host}], port=${port} ${proxy
          .fold("")("proxy=" + _.toString)}"
      )
    }

    if (binary)
      client.setFileType(FTP.BINARY_FILE_TYPE)

    if (passiveMode)
      client.enterLocalPassiveMode()

    client
  }

  def disconnect(): Unit =
    if (client.isConnected) {
      client.logout()
      client.disconnect()
    }

}
