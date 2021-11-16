/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.alpakka.ftp
package impl

import java.io.{IOException, InputStream, OutputStream}
import java.nio.file.attribute.PosixFilePermission

import akka.annotation.InternalApi
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.{OpenMode, RemoteResourceInfo, SFTPClient}
import net.schmizz.sshj.xfer.FilePermission

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.util.Try

/**
 * INTERNAL API
 */
@InternalApi
private[ftp] trait SftpOperations { _: FtpLike[SSHClient, SftpSettings] =>

  type ConnectionT = SftpConnection

  type Handler = SFTPClient

  override def newConnection(client: SSHClient, connectionSettings: SftpSettings): SftpConnection = {
    new SftpConnection(client, connectionSettings)
  }

  def listFiles(basePath: String, handler: Handler): immutable.Seq[FtpFile] = {
    val path = if (basePath.nonEmpty && basePath.head != '/') s"/$basePath" else basePath
    val entries = handler.ls(path).asScala
    entries.map { file =>
      FtpFile(
        file.getName,
        file.getPath,
        file.isDirectory,
        file.getAttributes.getSize,
        file.getAttributes.getMtime * 1000L,
        getPosixFilePermissions(file)
      )
    }.toVector
  }

  def mkdir(path: String, name: String, handler: Handler): Unit = {
    val updatedPath = CommonFtpOperations.concatPath(path, name)
    handler.mkdirs(updatedPath)
  }

  private def getPosixFilePermissions(file: RemoteResourceInfo) = {
    import PosixFilePermission._

    import FilePermission._
    file.getAttributes.getPermissions.asScala.collect {
      case USR_R => OWNER_READ
      case USR_W => OWNER_WRITE
      case USR_X => OWNER_EXECUTE
      case GRP_R => GROUP_READ
      case GRP_W => GROUP_WRITE
      case GRP_X => GROUP_EXECUTE
      case OTH_R => OTHERS_READ
      case OTH_W => OTHERS_WRITE
      case OTH_X => OTHERS_EXECUTE
    }.toSet
  }

  def listFiles(handler: Handler): immutable.Seq[FtpFile] = listFiles(".", handler)

  def retrieveFileInputStream(name: String, handler: Handler): Try[InputStream] =
    retrieveFileInputStream(name, handler, 0L)

  def retrieveFileInputStream(name: String, handler: Handler, offset: Long): Try[InputStream] =
    retrieveFileInputStream(name, handler, offset, 1)

  def retrieveFileInputStream(name: String,
                              handler: Handler,
                              offset: Long,
                              maxUnconfirmedReads: Int): Try[InputStream] =
    Try {
      val remoteFile = handler.open(name, java.util.EnumSet.of(OpenMode.READ))
      val is = maxUnconfirmedReads match {
        case m if m > 1 =>
          new remoteFile.ReadAheadRemoteFileInputStream(m, offset) {

            override def close(): Unit =
              try {
                super.close()
              } finally {
                remoteFile.close()
              }
          }
        case _ =>
          new remoteFile.RemoteFileInputStream(offset) {

            override def close(): Unit =
              try {
                super.close()
              } finally {
                remoteFile.close()
              }
          }
      }
      Option(is).getOrElse {
        remoteFile.close()
        throw new IOException(s"$name: No such file or directory")
      }
    }

  def storeFileOutputStream(name: String, handler: Handler, append: Boolean): Try[OutputStream] =
    Try {
      import OpenMode._
      val openModes =
        if (append) java.util.EnumSet.of(WRITE, CREAT, APPEND)
        else java.util.EnumSet.of(WRITE, CREAT, TRUNC)
      val remoteFile = handler.open(name, openModes)
      val os = new remoteFile.RemoteFileOutputStream() {

        override def close(): Unit = {
          try {
            remoteFile.close()
          } catch {
            case e: IOException =>
          }
          super.close()
        }
      }
      Option(os).getOrElse {
        remoteFile.close()
        throw new IOException(s"Could not write to $name")
      }
    }

  def move(fromPath: String, destinationPath: String, handler: Handler): Unit =
    handler.rename(fromPath, destinationPath)

  def remove(path: String, handler: Handler): Unit =
    handler.rm(path)
}
