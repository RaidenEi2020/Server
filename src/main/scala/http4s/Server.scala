package http4s

import cats.effect.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import com.comcast.ip4s.*
import org.http4s.HttpApp

object Server {

  private val httpApp: HttpApp[IO] =
    Router("/" -> Routes.routes).orNotFound

  def run: IO[Unit] =
    EmberServerBuilder.default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(httpApp)
      .build
      .use(_ => IO.never)
      .onError(e => IO.println(s"Error starting server: ${e.getMessage}"))
}
