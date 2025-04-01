package com.example.http4s

import cats.effect.IO
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import cats.effect.std.Supervisor

object Server {
  private val logger = LoggerFactory.getLogger(getClass)

  def logMetricsPeriodically(): IO[Unit] = {
    (IO.sleep(20.seconds) *> IO {
      val uptime = SystemStatus.getUptime()
      val memoryUsage = SystemStatus.getMemoryUsage()
      val threadCount = SystemStatus.getThreadCount()
      val systemLoad = SystemStatus.getSystemLoad()

      logger.info(
        s"""
           |Periodic Log:
           |-----------------------------
           |Uptime: $uptime
           |Memory Usage: $memoryUsage
           |Thread Count: $threadCount
           |System Load: $systemLoad
           |-----------------------------
       """.stripMargin
      )
    }).foreverM
  }


  def run: IO[Nothing] = {
    logger.info("Starting server...")

    Routes.routes.flatMap { httpRoutes =>
      val httpApp = Logger.httpApp(logHeaders = true, logBody = false)(httpRoutes.orNotFound)

      Supervisor[IO].use { supervisor =>
        supervisor.supervise(logMetricsPeriodically()) *> EmberServerBuilder.default[IO]
          .withHost(ipv4"127.0.0.1")
          .withPort(port"8080")
          .withHttpApp(httpApp)
          .build
          .useForever
          .onError { e =>
            IO(logger.error("Error: server couldn't start.", e))
          }
      }
    }
  }
}