/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.ftp.impl

import akka.annotation.InternalApi
import akka.stream.alpakka.ftp.FtpsSettings
import org.apache.commons.net.ftp.FTPSClient

/**
 * INTERNAL API
 */
@InternalApi
private[ftp] trait FtpsOperations extends CommonFtpOperations {
  _: FtpLike[FTPSClient, FtpsSettings] =>

  type ConnectionT = FtpsConnection

  override def newConnection(client: FTPSClient, connectionSettings: FtpsSettings): FtpsConnection = {
    new FtpsConnection(client, connectionSettings)
  }
}
