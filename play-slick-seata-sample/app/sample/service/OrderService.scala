package sample.service

import com.whitilied.play.seata.SeataManager
import io.seata.core.context.RootContext
import org.slf4j.LoggerFactory
import sample.model.{Order, OrderStatus}
import sample.repo.OrderRepo

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class OrderService @Inject()(
                              orderRepo: OrderRepo,
                              payService: PayService,
                              storageService: StorageService,
                            )(
                              implicit
                              ex: ExecutionContext
                            ) {

  private val logger = LoggerFactory.getLogger(getClass)

  def placeOrder(userId: Long, productId: Long, price: Int) = {
    SeataManager.asyncGlobalTx {
      logger.info("=============ORDER=================")
      logger.info("current xid: {}", RootContext.getXID)

      val amount = 1
      val order = Order(0, userId, productId, OrderStatus.INIT, price)

      for {
        saveOrderRecord <- orderRepo.saveOrder(order)
        operationStorageResult <- storageService.reduceStock(productId, amount)
        operationBalanceResult <- payService.reduceBalance(userId, price)
        updateOrderRecord <- orderRepo.updateOrder(order.id, OrderStatus.SUCCESS)
      } yield {
        logger.info("save order {}", if (saveOrderRecord > 0) "success" else "failure")
        logger.info("=============ORDER=================")
        logger.info("update order {} {}", order.id, if (updateOrderRecord > 0) "success" else "failure")

        operationStorageResult && operationBalanceResult
      }
    }
  }
}
