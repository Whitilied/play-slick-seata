package com.whitilied.play.seata


import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import play.api.db.slick.{DbName, SlickApi, SlickModule}
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger, PlayException}
import slick.SlickException
import slick.basic.{BasicProfile, DatabaseConfig}
import slick.jdbc.{JdbcBackend, JdbcDataSource}
import slick.util.{ClassLoaderUtil, SeataAsyncExecutor}
import slick.util.ConfigExtensionMethods.configExtensionMethods

import java.sql.Driver
import javax.inject.Inject
import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


final class SeataSlickApi @Inject()(
                                     configuration: Configuration,
                                     lifecycle: ApplicationLifecycle
                                   )(implicit executionContext: ExecutionContext)
  extends SlickApi {

  import SeataSlickApi.DatabaseConfigFactory

  private lazy val dbconfigFactoryByName: Map[DbName, DatabaseConfigFactory] = {
    def configs: Map[String, Config] = {
      val config = configuration.underlying
      val slickDbKey = config.getString(SlickModule.DbKeyConfig)
      if (config.hasPath(slickDbKey)) {
        val playConfig = Configuration(config)
        playConfig.get[Map[String, Config]](slickDbKey)
      } else Map.empty[String, Config]
    }

    (for ((name, config) <- configs) yield (DbName(name), new DatabaseConfigFactory(name, config, lifecycle))).toMap
  }

  // Be careful that accessing this field will trigger initialization of ALL database configs!
  private lazy val allDbConfigs: List[(DbName, DatabaseConfig[BasicProfile])] =
    dbconfigFactoryByName.map { case (name, factory) => (name, factory.get) }.toList

  def dbConfigs[P <: BasicProfile](): Seq[(DbName, DatabaseConfig[P])] =
    allDbConfigs.asInstanceOf[Seq[(DbName, DatabaseConfig[P])]]

  def dbConfig[P <: BasicProfile](name: DbName): DatabaseConfig[P] = {
    val factory: DatabaseConfigFactory = dbconfigFactoryByName.getOrElse(
      name,
      throw new PlayException(s"No database configuration found for ", name.value)
    )
    val dbConf: DatabaseConfig[BasicProfile] = factory.get
    dbConf.asInstanceOf[DatabaseConfig[P]]
  }
}

object SeataSlickApi {
  private object DatabaseConfigFactory {
    private val logger = Logger(classOf[SeataSlickApi])
  }

  // This class is useful for delaying the creation of `DatabaseConfig` instances.
  private class DatabaseConfigFactory(name: String, config: Config, lifecycle: ApplicationLifecycle)(
    implicit executionContext: ExecutionContext
  ) {

    import DatabaseConfigFactory.logger

    @throws(classOf[PlayException])
    lazy val get: DatabaseConfig[BasicProfile] = {
      val dbConf = create()
      logger.debug(s"Created Seata Slick database config for key $name.")
      registerDatabaseShutdownHook(dbConf)
      dbConf
    }

    @throws(classOf[PlayException])
    private def create(): DatabaseConfig[BasicProfile] = {
      try forConfig[BasicProfile](path = "", config = config)
        //      try DatabaseConfig.forConfig[BasicProfile](path = "", config = config)
      catch {
        case NonFatal(t) =>
          logger.error(s"Failed to create Slick database config for key $name.", t)
          throw Configuration(config).reportError(name, s"Cannot connect to database [$name]", Some(t))
      }
    }

    private def forConfig[P <: BasicProfile : ClassTag](path: String, config: Config = ConfigFactory.load(),
                                                        classLoader: ClassLoader = ClassLoaderUtil.defaultClassLoader): DatabaseConfig[P] = {
      val basePath = (if (path.isEmpty) "" else path + ".")
      val n = config.getStringOpt(basePath + "profile").getOrElse {
        val nOld = config.getStringOpt(basePath + "driver").map {
          case "slick.driver.DerbyDriver$" => "slick.jdbc.DerbyProfile$"
          case "slick.driver.H2Driver$" => "slick.jdbc.H2Profile$"
          case "slick.driver.HsqldbDriver$" => "slick.jdbc.HsqldbProfile$"
          case "slick.driver.MySQLDriver$" => "slick.jdbc.MySQLProfile$"
          case "slick.driver.PostgresDriver$" => "slick.jdbc.PostgresProfile$"
          case "slick.driver.SQLiteDriver$" => "slick.jdbc.SQLiteProfile$"
          case "slick.memory.MemoryDriver$" => "slick.memory.MemoryProfile$"
          case n => n
        }
        if (nOld.isDefined)
          logger.warn(s"Use `${basePath}profile` instead of `${basePath}driver`. The latter is deprecated since Slick 3.2 and will be removed.")
        nOld.getOrElse(config.getString(basePath + "profile")) // trigger the correct error
      }

      val untypedP = try {
        if (n.endsWith("$")) classLoader.loadClass(n).getField("MODULE$").get(null)
        else classLoader.loadClass(n).getConstructor().newInstance()
      } catch {
        case NonFatal(ex) =>
          throw new SlickException(s"""Error getting instance of profile "$n"""", ex)
      }
      val pClass = implicitly[ClassTag[P]].runtimeClass
      if (!pClass.isInstance(untypedP))
        throw new SlickException(s"Configured profile $n does not conform to requested profile ${pClass.getName}")
      val root = config
      new DatabaseConfig[P] {

        lazy val db: P#Backend#Database =
          createDatabase((if (path.isEmpty) "" else path + ".") + "db", root).asInstanceOf[P#Backend#Database]
        val profile: P = untypedP.asInstanceOf[P]
        val driver: P = untypedP.asInstanceOf[P]
        lazy val config: Config = if (path.isEmpty) root else root.getConfig(path)

        def profileName = if (profileIsObject) n.substring(0, n.length - 1) else n

        def profileIsObject = n.endsWith("$")
      }
    }

    private def createDatabase(path: String, config: Config = null, driver: Driver = null,
                               classLoader: ClassLoader = ClassLoaderUtil.defaultClassLoader): JdbcBackend#DatabaseDef = {
      val initializedConfig = if (config eq null) ConfigFactory.load(classLoader) else config
      val usedConfig = if (path.isEmpty) initializedConfig else initializedConfig.getConfig(path)
      val connectionPool = SeataDataSourceProxy.getClass.getName
      logger.debug(s"connectionPool:${connectionPool}")
      val connectionPoolValue = ConfigValueFactory.fromAnyRef(connectionPool)
      // overwrite connectionPool
      val overrideConfig = usedConfig.withValue("connectionPool", connectionPoolValue)
      logger.debug(s"overrideConfig:${overrideConfig}")
      val source: JdbcDataSource = JdbcDataSource.forConfig(overrideConfig, driver, path, classLoader)
      val poolName = overrideConfig.getStringOr("poolName", path)
      val numThreads = overrideConfig.getIntOr("numThreads", 20)
      val maxConnections = source.maxConnections.getOrElse(numThreads)
      val executor = SeataAsyncExecutor.apply(poolName, numThreads, numThreads, usedConfig.getIntOr("queueSize", 1000), maxConnections)
      new JdbcBackend.DatabaseDef(source, executor)
    }

    private def registerDatabaseShutdownHook(dbConf: DatabaseConfig[_]): Unit = {
      // clean-up when the application is stopped.
      lifecycle.addStopHook { () =>
        Future {
          Try(dbConf.db.close()) match {
            case Success(_) => logger.debug(s"Database $name was successfully closed.")
            case Failure(t) => logger.warn(s"Error occurred while closing database $name.", t)
          }
        }
      }
    }
  }

}

