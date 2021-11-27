/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.ftp
package impl

import net.schmizz.sshj.SSHClient
import org.apache.commons.net.ftp.{FTPClient, FTPSClient}

import scala.collection.immutable
import scala.util.Try
import java.io.{InputStream, OutputStream}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}

import akka.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
protected[ftp] trait FtpLike[FtpClient, S <: RemoteFileSettings] {

  type ConnectionT <: Connection[FtpClient, S]

  type Handler

  // remote hosts will time out connections after a short time
  // if a cached connection was put in the cache more than this many millis ago, discard it instead of using it
  // In practice during a series of file downloads, a connection will sit in the cache for only a fraction of a second
  val ConnectionCacheTimeout = 60000

  // connectionSettings.toString -> a queue containing cached ConnectionT objects
  val connectionCache = new ConcurrentHashMap[String, ConcurrentLinkedQueue[ConnectionT]]()

  def newConnection(client: FtpClient, connectionSettings: S): ConnectionT

  def getCachedConnection(connectionSettings: S): Option[ConnectionT] = {
    val maxTimeCached = System.currentTimeMillis() - ConnectionCacheTimeout
    val queue = connectionCache.computeIfAbsent(connectionSettings.toString, _ => new ConcurrentLinkedQueue())
    var conn: Option[ConnectionT] = Option.empty[ConnectionT]

    while (!queue.isEmpty && conn.isEmpty) {
      val c = queue.poll()
      if (c != null) {
        if (c.timeCached < maxTimeCached)
          Try { c.disconnect() }
        else conn = Some(c)
      }
    }

    conn
  }

  def putConnectionInCache(conn: ConnectionT): Unit = {
    conn.timeCached = System.currentTimeMillis()
    val queue = connectionCache.computeIfAbsent(conn.connectionSettings.toString, _ => new ConcurrentLinkedQueue())
    queue.add(conn)
  }

  def closeAllCachedConnections(): Unit = {
    connectionCache.values().forEach(queue => queue.forEach(conn => Try { conn.disconnect() }))
    connectionCache.clear()
  }

  // TODO there's probably a better way of cleaning up all the cached connections. ActorSystem.registerOnTermination?
  Runtime.getRuntime.addShutdownHook(new Thread("Clean cached ftp connections") {
    override def run(): Unit = closeAllCachedConnections()
  })

  def listFiles(basePath: String, handler: Handler): immutable.Seq[FtpFile]

  def listFiles(handler: Handler): immutable.Seq[FtpFile]

  def retrieveFileInputStream(name: String, handler: Handler): Try[InputStream]

  def storeFileOutputStream(name: String, handler: Handler, append: Boolean): Try[OutputStream]

  def move(fromPath: String, destinationPath: String, handler: Handler): Unit

  def remove(path: String, handler: Handler): Unit

  def mkdir(path: String, name: String, handler: Handler): Unit
}

/**
 * INTERNAL API
 */
@InternalApi
protected[ftp] trait RetrieveOffset { _: FtpLike[_, _] =>

  def retrieveFileInputStream(name: String, handler: Handler, offset: Long): Try[InputStream]

}

/**
 * INTERNAL API
 */
@InternalApi
protected[ftp] trait UnconfirmedReads { _: FtpLike[_, _] =>

  def retrieveFileInputStream(name: String, handler: Handler, offset: Long, maxUnconfirmedReads: Int): Try[InputStream]

}

/**
 * INTERNAL API
 */
@InternalApi
object FtpLike {
  // type class instances
  implicit val ftpLikeInstance =
    new FtpLike[FTPClient, FtpSettings] with RetrieveOffset with FtpOperations
  implicit val ftpsLikeInstance =
    new FtpLike[FTPSClient, FtpsSettings] with RetrieveOffset with FtpsOperations
  implicit val sFtpLikeInstance =
    new FtpLike[SSHClient, SftpSettings] with RetrieveOffset with SftpOperations with UnconfirmedReads
}
