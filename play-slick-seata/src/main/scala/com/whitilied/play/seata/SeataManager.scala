package com.whitilied.play.seata

import com.typesafe.config.ConfigFactory
import io.seata.rm.RMClient
import io.seata.tm.TMClient
import io.seata.tm.api.GlobalTransactionContext
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object SeataManager {

  private val logger = LoggerFactory.getLogger(getClass)

  val config = ConfigFactory.load()

  val applicationId = config.getString("seata.applicationId")
  val txServiceGroup = config.getString("seata.serviceGroup")

  TMClient.init(applicationId, txServiceGroup)
  RMClient.init(applicationId, txServiceGroup)

  def globalTx[T](f: => T, timeout: Int = 60000)
  : T = {
    val tx = GlobalTransactionContext.getCurrentOrCreate
    tx.begin(timeout)
    logger.debug(s"current xid:${tx.getXid}")
    try {
      val r = f
      tx.commit()
      logger.debug(s"tx commit, xid:${tx.getXid}")
      r
    } catch {
      case NonFatal(e) =>
        tx.rollback()
        logger.debug(s"tx rollback, xid:${tx.getXid}")
        throw e
    }
  }


  def asyncGlobalTx[T](f: => Future[T], timeout: Int = 60000)
                      (implicit ec: ExecutionContext)
  : Future[T] = {
    val tx = GlobalTransactionContext.getCurrentOrCreate
    tx.begin(timeout)
    logger.debug(s"current xid:${tx.getXid}")
    f.map { r =>
      tx.commit()
      logger.debug(s"tx commit, xid:${tx.getXid}")
      r
    }.recover {
      case NonFatal(e) =>
        tx.rollback()
        logger.debug(s"tx rollback, xid:${tx.getXid}")
        throw e
    }
  }

}
