package com.example.http4s

import cats.effect.IOApp

object Main extends IOApp.Simple {
  val run = Server.run
}