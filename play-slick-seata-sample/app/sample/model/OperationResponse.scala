package sample.model

import play.api.libs.json.Json


case class OperationResponse(success: Boolean, message: Option[String] = None)

object OperationResponse {
  implicit val jsonSer = Json.format[OperationResponse]
}