package com.whitilied.play.seata

import com.typesafe.config.Config
import io.seata.rm.datasource.DataSourceProxy
import slick.jdbc.{JdbcDataSource, JdbcDataSourceFactory}
import slick.util.ConfigExtensionMethods.configExtensionMethods

import java.sql.{Connection, Driver}

class SeataDataSourceProxy(val ds: com.zaxxer.hikari.HikariDataSource) extends JdbcDataSource {

  lazy val proxy = new DataSourceProxy(ds)

  def createConnection(): Connection = proxy.getConnection

  def close(): Unit = ds.close()

  override val maxConnections: Option[Int] = Some(ds.getMaximumPoolSize)
}

object SeataDataSourceProxy extends JdbcDataSourceFactory {

  def forConfig(c: Config, driver: Driver, name: String, classLoader: ClassLoader): SeataDataSourceProxy = {
    println("##SeataDataSourceProxy.forConfig")
    import com.zaxxer.hikari._

    val hconf = new HikariConfig()

    // Essential settings

    // Use HikariCP `dataSourceClassName` as the main configuration and fallback to
    // `dataSourceClass`, `driverClassName` and finally `drive`.
    c.getStringOpt("dataSourceClassName").orElse(c.getStringOpt("dataSourceClass")) match {
      case Some(className) => hconf.setDataSourceClassName(className)
      case None => c.getStringOpt("driverClassName").orElse(c.getStringOpt("driver")).foreach(hconf.setDriverClassName)
    }

    // Use `jdbcUrl` an then `url` to configure the pool. According to HikariCP docs, when
    // using this property with "old" drivers, you may also need to set the driverClassName
    // property.
    c.getStringOpt("jdbcUrl").orElse(c.getStringOpt("url")).foreach(hconf.setJdbcUrl)
    c.getStringOpt("username").orElse(c.getStringOpt("user")).foreach(hconf.setUsername)
    c.getStringOpt("password").foreach(hconf.setPassword)

    c.getPropertiesOpt("properties").foreach(hconf.setDataSourceProperties)

    // Frequently used pool configuration
    c.getBooleanOpt("autoCommit").foreach(hconf.setAutoCommit)

    val numThreads = c.getIntOr("numThreads", 20)

    hconf.setConnectionTimeout(c.getMillisecondsOr("connectionTimeout", 30000))
    hconf.setIdleTimeout(c.getMillisecondsOr("idleTimeout", 600000))
    hconf.setMaxLifetime(c.getMillisecondsOr("maxLifetime", 1800000))
    c.getStringOpt("connectionTestQuery").foreach(hconf.setConnectionTestQuery)
    c.getStringOpt("connectionInitSql").foreach(hconf.setConnectionInitSql)
    hconf.setMaximumPoolSize(c.getIntOpt("maximumPoolSize").orElse(c.getIntOpt("maxConnections")).getOrElse(numThreads))
    hconf.setMinimumIdle(c.getIntOpt("minimumIdle").orElse(c.getIntOpt("minConnections")).getOrElse(numThreads))
    hconf.setPoolName(c.getStringOr("poolName", name))

    // Infrequently used

    // The default value for `initializationFailFast` was false, which means the pool
    // will not fail to start if there is a problem when connecting to the database.
    // To keep this behavior, we need to set `initializationFailTimeout` to -1 as
    // documented by HikariCP.
    hconf.setInitializationFailTimeout(c.getMillisecondsOr("initializationFailTimeout", default = {
      // `initializationFailFast` is deprecated and should be replaced by
      // `initializationFailTimeout`. See HikariCP docs for more information:
      // https://github.com/brettwooldridge/HikariCP#infrequently-used
      // But for backwards compatibility we check for it if initializationFailTimeout is missing
      c.getBooleanOpt("initializationFailFast").map { failFast =>
        if (failFast) 1L
        else 0L
      }.getOrElse(-1L)
    }))

    c.getBooleanOpt("isolateInternalQueries").foreach(hconf.setIsolateInternalQueries)
    c.getBooleanOpt("allowPoolSuspension").foreach(hconf.setAllowPoolSuspension)
    c.getBooleanOpt("readOnly").foreach(hconf.setReadOnly)
    c.getBooleanOpt("registerMbeans").foreach(hconf.setRegisterMbeans)
    c.getStringOpt("catalog").foreach(hconf.setCatalog)
    c.getStringOpt("connectionInitSql").foreach(hconf.setConnectionInitSql)
    c.getStringOpt("transactionIsolation")
      .orElse(c.getStringOpt("isolation"))
      .map("TRANSACTION_" + _)
      .foreach(hconf.setTransactionIsolation)

    hconf.setValidationTimeout(c.getMillisecondsOr("validationTimeout", 5000))
    hconf.setLeakDetectionThreshold(c.getMillisecondsOr("leakDetectionThreshold", 0))

    c.getStringOpt("schema").foreach(hconf.setSchema)

    val dataSource = new HikariDataSource(hconf)
    new SeataDataSourceProxy(dataSource)
  }



}
