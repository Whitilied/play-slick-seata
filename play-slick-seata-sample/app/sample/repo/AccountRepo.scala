package sample.repo

import sample.model.Account
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AccountRepo @Inject() (@NamedDatabase("pay") dbConfigProvider: DatabaseConfigProvider)
                            (implicit ec: ExecutionContext) {

  val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  private class AccountTable(tag: Tag) extends Table[Account](tag, "account") {

    def userId = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def balance = column[Int]("balance")

    def * = (userId, balance) <> (Account.tupled, Account.unapply)
  }

  private val accounts = TableQuery[AccountTable]

  def getBalance(userId: Long) = {
    val action = accounts
      .filter(_.userId === userId)
      .map(_.balance)
      .result
      .headOption
    db.run(action)
  }

  def reduceBalance(price: Int) = {
    val action = sqlu"UPDATE account SET balance = balance - $price WHERE id = 1"
    db.run(action)
  }

}
