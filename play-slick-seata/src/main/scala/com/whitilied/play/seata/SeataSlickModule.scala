package com.whitilied.play.seata

import play.api.db.slick.{DatabaseConfigProvider, DbName, SlickApi, SlickModule}
import play.api.inject.{Binding, BindingKey, Module}
import play.api.{Configuration, Environment}
import play.db.NamedDatabaseImpl
import slick.basic.{BasicProfile, DatabaseConfig}
import slick.util.Logging

import javax.inject.{Inject, Provider, Singleton}
import scala.collection.immutable.Seq

@Singleton
final class SlickSeataModule extends Module {

  def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    val config  = configuration.underlying
    val dbKey   = config.getString(SlickModule.DbKeyConfig)
    val default = config.getString(SlickModule.DefaultDbName)
    val dbs     = configuration.getOptional[Configuration](dbKey).getOrElse(Configuration.empty).subKeys
    Seq(bind[SlickApi].to[SeataSlickApi].in[Singleton]) ++ namedDatabaseConfigBindings(dbs) ++ defaultDatabaseConfigBinding(
      default,
      dbs
    )
  }

  def namedDatabaseConfigBindings(dbs: Set[String]): Seq[Binding[_]] = dbs.toList.map { db =>
    bindNamed(db).to(new NamedDatabaseConfigProvider(db))
  }

  def defaultDatabaseConfigBinding(default: String, dbs: Set[String]): Seq[Binding[_]] =
    if (dbs.contains(default)) Seq(bind[DatabaseConfigProvider].to(bindNamed(default))) else Nil

  def bindNamed(name: String): BindingKey[DatabaseConfigProvider] =
    bind[DatabaseConfigProvider].qualifiedWith(new NamedDatabaseImpl(name))
}

/** Inject provider for named databases. */
final class NamedDatabaseConfigProvider(name: String) extends Provider[DatabaseConfigProvider] with Logging {
  @Inject private var slickApi: SlickApi = _

  logger.info(s"db config provider, db name:${name}")

  lazy val get: DatabaseConfigProvider = new DatabaseConfigProvider {
    def get[P <: BasicProfile]: DatabaseConfig[P] = slickApi.dbConfig[P](DbName(name))
  }
}
