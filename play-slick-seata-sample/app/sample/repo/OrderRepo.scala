package sample.repo

import sample.model.OrderStatus.OrderStatus
import sample.model.{Order, OrderStatus}
import play.api.db.slick.DatabaseConfigProvider
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class OrderRepo @Inject()(dbConfigProvider: DatabaseConfigProvider)
                         (implicit ec: ExecutionContext) {

  val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  implicit val orderStatsMapper = MappedColumnType.base[OrderStatus, String](
    e => e.toString,
    s => OrderStatus.withName(s)
  )

  private class OrderTable(tag: Tag) extends Table[Order](tag, "orders") {

    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def userId = column[Long]("user_id")

    def productId = column[Long]("product_id")

    def status = column[OrderStatus]("status")

    def payAmount = column[Int]("pay_amount")

    def * = (id, userId, productId, status, payAmount) <> (Order.tupled, Order.unapply)
  }

  private val orders = TableQuery[OrderTable]

  def saveOrder(order: Order) = {
    val action = orders += order
    db.run(action)
  }

  def updateOrder(id: Long, status: OrderStatus) = {
    val action = orders.filter(_.id === id).map(_.status).update(status)
    db.run(action)
  }

}
