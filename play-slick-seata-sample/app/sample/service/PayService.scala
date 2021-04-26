package sample.service

import sample.repo.AccountRepo
import io.seata.core.context.RootContext
import org.slf4j.LoggerFactory

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class PayService @Inject()(
                            accountRepo: AccountRepo
                          )(
                            implicit
                            ex: ExecutionContext
                          ) {

  private val logger = LoggerFactory.getLogger(getClass)

  def reduceBalance(userId: Long, price: Int) = {
    logger.info("=============PAY=================")
    logger.info("current xid: {}", RootContext.getXID)

    checkBalance(userId, price)
      .flatMap { _ =>
        logger.info("before deduction balance {} ", userId)
        accountRepo.reduceBalance(price)
          .map { record =>
            val result = record > 0
            logger.info("deduction balance {} current balance:{}", userId, if (result) "success" else "failure")
            result
          }
      }
  }

  private def checkBalance(userId: Long, price: Int) = {
    logger.info("user balance check, user {}", userId)
    accountRepo.getBalance(userId).map { balance =>
      if (balance.getOrElse(0) < price) {
        logger.warn("user {} balance is not enough，current balance {}", userId, balance)
        throw new Exception("balance is not enough")
      }
      balance
    }
  }

}
