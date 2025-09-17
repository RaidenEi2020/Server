package http4s.utils

import cats.effect._
import cats.effect.std.Semaphore
import org.http4s.Response
import org.http4s.dsl.io._
import scala.sys.process._
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import cats.effect.unsafe.implicits.global


object ProcessManager {

  case class ProcessData(id: Int, token: String, cmd: String, output: Option[String])

  private val processCounter = new AtomicInteger(1)
  private val pendingQueue = new LinkedBlockingQueue[ProcessData]()
  private val runningProcesses = new LinkedBlockingQueue[(Int, Process)]()
  private val processHistory = new LinkedBlockingQueue[ProcessData]()

  private val maxProcesses = 3
  private val semaphore: Semaphore[IO] = Semaphore[IO](maxProcesses).unsafeRunSync()

  def getMaxProcesses: Int = maxProcesses

  def getPendingQueue: LinkedBlockingQueue[ProcessData] = pendingQueue

  def getRunningProcesses: LinkedBlockingQueue[(Int, Process)] = runningProcesses

  def getProcessHistory: LinkedBlockingQueue[ProcessData] = processHistory

  def runProcess(process: ProcessData): IO[Response[IO]] = {
    val outputBuilder = new StringBuilder
    val logger = ProcessLogger(
      line => outputBuilder.append(line).append("\n"),
      err => outputBuilder.append("[ERROR] ").append(err).append("\n")
    )
    for {
      processWithOutput <- IO(Process(s"""bash -c '${process.cmd}'""").run(logger))
      _ <- IO.blocking(getRunningProcesses.add(process.id, processWithOutput))
      exitCode <- IO(processWithOutput.exitValue())
      output = outputBuilder.toString + s"\nProcess exited with code $exitCode."
      _ <- IO.blocking(getRunningProcesses.remove((process.id, processWithOutput)))
      _ <- IO {
        val processDataWithOutput = ProcessData(process.id, process.token, process.cmd, Some(output))
        getProcessHistory.add(processDataWithOutput)
      }
      res <- Ok(s"Process started!\nId: ${process.id}\nCommand: ${process.cmd}\nUser: ${process.token}\nOutput:\n$output")
    } yield res
  }

  def waitAndRunProcess(token: String, cmd: String): IO[Response[IO]] = {
    val processId = processCounter.getAndIncrement()
    val process = ProcessData(processId, token, cmd, None)
    pendingQueue.put(process)
    semaphore.permit.use { _ =>
      for {
        process <- IO.blocking(pendingQueue.take())
        res <- runProcess(process).handleErrorWith(e => InternalServerError(s"Process failed: ${e.getMessage}"))
      } yield res
    }
  }

  def resetAll: Unit = {
    processCounter.set(1)
    getPendingQueue.clear()
    getRunningProcesses.forEach { case (_, process) => process.destroy() }
    getRunningProcesses.clear()
    getProcessHistory.clear()
  }
}
