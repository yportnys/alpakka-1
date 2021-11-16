/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.ftp
package impl

import java.io.IOException

import akka.annotation.InternalApi
import akka.stream.Shape
import akka.stream.stage.GraphStageLogic

import scala.util.Try
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
@InternalApi
private[ftp] abstract class FtpGraphStageLogic[T, FtpClient, S <: RemoteFileSettings](
    val shape: Shape,
    val ftpLike: FtpLike[FtpClient, S],
    val connectionSettings: S,
    val ftpClient: () => FtpClient
) extends GraphStageLogic(shape) {

  protected[this] var connection: Option[ftpLike.ConnectionT] = Option.empty[ftpLike.ConnectionT]
  protected[this] var handler: Option[ftpLike.Handler] = Option.empty[ftpLike.Handler]
  protected[this] var failed = false

  override def preStart(): Unit = {
    super.preStart()

    try {
      val tryConnect = Try {
        var cachedConnection: Option[ftpLike.ConnectionT] = None
        if (connectionSettings.reuseConnections)
          cachedConnection = ftpLike.getCachedConnection(connectionSettings)

        if (cachedConnection.isEmpty) ftpLike.newConnection(ftpClient(), connectionSettings) else cachedConnection.get
      }

      if (tryConnect.isSuccess) {
        connection = tryConnect.toOption
        // TODO is there a way to avoid this type cast?
        handler = Some(connection.get.handler.asInstanceOf[ftpLike.Handler])
      } else
        tryConnect.failed.foreach { case NonFatal(t) => throw t }

      doPreStart()
    } catch {
      case NonFatal(t) =>
        matFailure(t)
        failStage(t)
    }
  }

  override def postStop(): Unit = {
    try {
      if (connectionSettings.reuseConnections)
        ftpLike.putConnectionInCache(connection.get)
      else disconnect()
    } catch {
      case e: IOException =>
        matFailure(e)
        // If we're failing, we might not be able to cleanly shut down the connection.
        // So swallow any IO exceptions
        if (!failed) throw e
      case NonFatal(e) =>
        matFailure(e)
        throw e
    }
    matSuccess()
    super.postStop()
  }

  protected[this] def doPreStart(): Unit

  protected[this] def disconnect(): Unit =
    connection.foreach(_.disconnect())

  protected[this] def matSuccess(): Boolean

  protected[this] def matFailure(t: Throwable): Boolean

}
