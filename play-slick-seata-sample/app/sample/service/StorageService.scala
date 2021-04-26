package sample.service

import sample.repo.ProductRepo
import io.seata.core.context.RootContext
import org.slf4j.LoggerFactory

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class StorageService @Inject()(
                                productRepo: ProductRepo
                              )(
                                implicit
                                ex: ExecutionContext
                              ) {

  private val logger = LoggerFactory.getLogger(getClass)

  def reduceStock(productId: Long, amount: Int) = {
    logger.info("=============STORAGE=================")
    logger.info("current xid: {}", RootContext.getXID)
    // checkStock
    checkStock(productId, amount)
      .flatMap { _ =>
        logger.info("start reduce {} stock", productId)
        // 扣减库存
        productRepo.reduceStock(productId, amount).map { record =>
          val result = record > 0
          logger.info("reduce {} stock result:{}", productId, if (result) "success" else "failure")
          result
        }
      }
  }

  private def checkStock(productId: Long, requiredAmount: Int): Future[Option[Int]] = {
    logger.info("check {} stock", productId)
    productRepo.getStock(productId).map { stock =>
      if (stock.getOrElse(0) < requiredAmount) {
        logger.warn("{} stock is not enough. current stock:{}", productId, stock)
        throw new Exception("stock is not enough")
      }
      stock
    }
  }

}
