package sample.model

object OrderStatus extends Enumeration {
  type OrderStatus = Value
  val INIT = Value("INIT")
  val SUCCESS = Value("SUCCESS")
  val FAIL = Value("FAIL")
}
