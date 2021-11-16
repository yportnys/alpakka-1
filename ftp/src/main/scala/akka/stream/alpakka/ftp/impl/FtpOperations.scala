/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.ftp
package impl

import akka.annotation.InternalApi
import org.apache.commons.net.ftp.FTPClient

/**
 * INTERNAL API
 */
@InternalApi
private[ftp] trait FtpOperations extends CommonFtpOperations { _: FtpLike[FTPClient, FtpSettings] =>

  type ConnectionT = FtpConnection

  override def newConnection(client: Handler, connectionSettings: FtpSettings): FtpConnection = {
    new FtpConnection(client, connectionSettings)
  }

}
