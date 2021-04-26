package sample.controller


import com.typesafe.config.ConfigFactory
import io.seata.rm.RMClient
import io.seata.tm.TMClient
import org.slf4j.LoggerFactory
import play.api.libs.json.Json
import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}
import sample.model.OperationResponse
import sample.model.vo.PlaceOrderRequestVO
import sample.service.OrderService

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class OrderController @Inject()(
                                 orderService: OrderService,
                                 cc: MessagesControllerComponents,
                               )(
                                 implicit
                                 ex: ExecutionContext
                               )
  extends MessagesAbstractController(cc) {

  import OperationResponse.jsonSer
  import PlaceOrderRequestVO.jsonDeser

  private val logger = LoggerFactory.getLogger(getClass)

  def placeOrder() = Action.async(parse.json[PlaceOrderRequestVO]) { req =>
    val reqVO = req.body
    logger.info(s"handler order request, {}", reqVO)
    orderService.placeOrder(reqVO.userId, reqVO.productId, reqVO.price)
      .map { result =>
        Ok(Json.toJson(OperationResponse(result)))
      }
  }

}
