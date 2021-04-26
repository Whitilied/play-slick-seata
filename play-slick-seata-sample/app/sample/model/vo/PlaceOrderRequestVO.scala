package sample.model.vo

import play.api.libs.json.{Format, Json}

case class PlaceOrderRequestVO(userId: Long, productId: Long, price: Int)

object PlaceOrderRequestVO {
  implicit val jsonDeser: Format[PlaceOrderRequestVO] = Json.format[PlaceOrderRequestVO]
}
