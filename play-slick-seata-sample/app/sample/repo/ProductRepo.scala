package sample.repo

import sample.model.Product
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ProductRepo @Inject()(@NamedDatabase("storage") dbConfigProvider: DatabaseConfigProvider)
                           (implicit ec: ExecutionContext) {

  val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  private class ProductTable(tag: Tag) extends Table[Product](tag, "product") {

    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def price = column[BigDecimal]("price")

    def stock = column[Int]("stock")

    def * = (id, price, stock) <> (Product.tupled, Product.unapply)
  }

  private val products = TableQuery[ProductTable]

  def getStock(productId: Long) = {
    val action = products
      .filter(_.id === productId)
      .map(_.stock)
      .result
      .headOption
    db.run(action)
  }


  def reduceStock(productId: Long, amount: Int) = {
    val action = sqlu"UPDATE product SET stock = stock - ${amount} WHERE id = ${productId}"
    db.run(action)
  }

}
