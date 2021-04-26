version := "0.1"

val scala212Version = "2.12.13"

val seataVersion = "1.4.1"

val playSlickVersion = "5.0.0"

val mysqlVersion = "5.1.44"

scalafmtOnCompile in Compile := true

lazy val `play-slick-seata-root` = (project in file("."))
  .aggregate(
    `play-slick-seata`,
    `play-slick-seata-sample`
  )

lazy val `play-slick-seata` = (project in file("play-slick-seata"))
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play"     %%      "play-slick"          %       playSlickVersion,
      "io.seata"              %       "seata-all"           %       seataVersion
    )
  )

lazy val `play-slick-seata-sample` = (project in file("play-slick-seata-sample"))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      guice,
      "mysql"                 %   "mysql-connector-java"    % mysqlVersion
    )
  )
  .dependsOn(`play-slick-seata`)


scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Ywarn-value-discard",
  "-Ywarn-dead-code",
  "-Xlint:infer-any",
  "-Xlint:nullary-override",
  "-Xlint:nullary-unit",
  "-Xfatal-warnings"
)
