/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.ftp.impl

import akka.annotation.InternalApi
import akka.stream.alpakka.ftp.{FtpAuthenticationException, FtpsSettings}
import org.apache.commons.net.ftp.{FTP, FTPSClient}

/**
 * INTERNAL API
 */
@InternalApi
private[ftp] class FtpsConnection(client: FTPSClient, connectionSettings: FtpsSettings)
    extends Connection[FTPSClient, FtpsSettings](client, connectionSettings) {

  override type Handler = FTPSClient

  override def connect(): FTPSClient = {
    val conn = connectionSettings
    import conn._

    proxy.foreach(client.setProxy)

    client.connect(host, port)

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
