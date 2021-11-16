/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.ftp.impl

import akka.annotation.InternalApi
import akka.stream.alpakka.ftp.RemoteFileSettings

/**
 * INTERNAL API
 */
@InternalApi
private[ftp] abstract class Connection[FtpClient, S <: RemoteFileSettings](val client: FtpClient,
                                                                           val connectionSettings: S) {

  type Handler

  val handler: Handler = connect()

  // the last time this connection was put in the cache and started being idle
  var timeCached: Long = -1

  def connect(): Handler

  def disconnect(): Unit

}
