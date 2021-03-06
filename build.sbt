organization := "com.janrain"

name := "rpscala"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.9.2"

scalacOptions += "-deprecation"

// XXX: Compile in debug mode, otherwise spring throws exceptions
javacOptions += "-g"

compileOrder := CompileOrder.ScalaThenJava

javaOptions += "-DCONFIG_PATH=/opt/rpscala"

initialize ~= { _ =>
  System.setProperty("CONFIG_PATH", "/opt/rpscala")
}

seq(webSettings :_*)

port in container.Configuration := 8081

classpathTypes ~= (_ + "orbit")

libraryDependencies ++= Seq(
  "org.scalatra" % "scalatra" % "2.1.1",
  "org.scalatra" % "scalatra-scalate" % "2.1.1",
  "org.scalatra" % "scalatra-specs2" % "2.1.1" % "test",
  "commons-httpclient" % "commons-httpclient" % "3.1",
  //"log4j" % "log4j" % "1.2.17",
  "ch.qos.logback" % "logback-classic" % "1.0.6",
  "org.slf4j" % "slf4j-log4j12" % "1.7.2",
  "org.openid4java" % "openid4java" % "0.9.7",
  "org.eclipse.jetty" % "jetty-webapp" % "8.1.7.v20120910" % "container",
  "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
)

resolvers ++= Seq(
)
