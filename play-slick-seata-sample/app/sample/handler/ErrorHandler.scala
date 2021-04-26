package sample.handler

import play.api.http.HttpErrorHandler
import play.api.libs.json.Json
import play.api.mvc._
import play.api.mvc.Results._
import sample.model.OperationResponse

import scala.concurrent._
import javax.inject.Singleton

@Singleton
class ErrorHandler extends HttpErrorHandler {

  import OperationResponse.jsonSer

  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    Future.successful(
      Status(statusCode)(Json.toJson(OperationResponse(false, Some(message))))
    )
  }

  def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {

    Future.successful(
      InternalServerError(Json.toJson(OperationResponse(false, Option(exception.getMessage))))
    )
  }
}