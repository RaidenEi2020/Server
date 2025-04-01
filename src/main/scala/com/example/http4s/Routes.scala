package com.example.http4s

import cats.effect.{IO, Ref}
import org.http4s._
import org.http4s.dsl.io._
import org.slf4j.LoggerFactory
import scala.sys.process._
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import cats.effect.unsafe.implicits.global
import scala.jdk.CollectionConverters._
import scala.concurrent.duration.DurationInt

object Routes {
  private val logger = LoggerFactory.getLogger(getClass)
  private val processRef: IO[Ref[IO, Map[Int, Process]]] = Ref.of[IO, Map[Int, Process]](Map())
  private val outputRef: IO[Ref[IO, Map[Int, String]]] = Ref.of[IO, Map[Int, String]](Map())
  private val pendingQueue = new LinkedBlockingQueue[(String, String)]()
  private val processCounter = new AtomicInteger(1)
  private val maxProcesses = 3

  val routes: IO[HttpRoutes[IO]] = for {
    processes <- processRef
    outputs <- outputRef
  } yield HttpRoutes.of[IO] {

    case GET -> Root / "status" =>
      val uptime = SystemStatus.getUptime()
      val memoryUsage = SystemStatus.getMemoryUsage()
      val threadCount = SystemStatus.getThreadCount()
      val systemLoad = SystemStatus.getSystemLoad()
      logger.info(
        s"\nRoute: /status " +
          s"\nUptime: $uptime " +
          s"\nMemory Usage: $memoryUsage " +
          s"\nThread Number: $threadCount " +
          s"\nSystem Load: $systemLoad")
      Ok(
        s"""
           |Server Status:
           |-----------------------------
           |Uptime: $uptime
           |Memory Usage: $memoryUsage
           |Thread Count: $threadCount
           |System Load: $systemLoad
           |-----------------------------
           |Server is operational.
           """.stripMargin
      )

    case req @ GET -> Root / "start-process" =>
      val cmdOpt = req.uri.query.params.get("cmd")
      val userIp = req.remoteAddr.getOrElse("unknown")

      cmdOpt match {
        case Some(cmd) =>
          processes.get.flatMap { procs =>
            if (procs.size >= maxProcesses) {
              val queueContainsIp = pendingQueue.synchronized {
                pendingQueue.asScala.exists { case (ip, _) => ip == userIp.toString }
              }

              if (queueContainsIp) {
                Ok("⚠️ You already have a pending command in the queue.")
              } else {
                pendingQueue.offer((userIp.toString, cmd))
                Ok(s"🚀 Process limit reached. Command added to queue.")
              }
            } else {
              startProcess(cmd, userIp.toString, processes, outputs)
            }
          }.map(addCORSHeaders)

        case None => BadRequest("⚠️ Command not provided. Use /start-process?cmd=<your_command>").map(addCORSHeaders)
      }


    case req @ GET -> Root / "stop-process" =>
      val idOpt = req.uri.query.params.get("id").flatMap(idStr => scala.util.Try(idStr.toInt).toOption)

      idOpt match {
        case Some(id) =>
          processes.modify { procs =>
            procs.get(id) match {
              case Some(process) =>
                process.destroy()
                (procs - id, Some(id))
              case None =>
                (procs, None)
            }
          }.flatMap {
            case Some(_) =>
              checkQueue(processes, outputs) *>
                Ok(s"Process $id stopped!")
            case None => NotFound(s"Process $id not found.")
          }

        case None => BadRequest("Unknown or invalid ID. Use /stop-process?id=<id>")
      }

    case GET -> Root / "process-output" =>
      outputs.get.flatMap { outputMap =>
        val outputJson = outputMap.map { case (id, output) =>
          s""""$id": "${output.replace("\n", "\\n")}""""
        }.mkString("{", ",", "}")

        Ok(outputJson).map(_.withContentType(org.http4s.headers.`Content-Type`(MediaType.application.json)))
      }

    case GET -> Root / "process-control" =>
      processes.get.flatMap { procs =>
        outputs.get.flatMap { outputMap =>
          IO.blocking {
            val queueSnapshot = pendingQueue.asScala.toList
            val queueList = queueSnapshot.map { case (ip, cmd) => s"<li>$ip: $cmd</li>" }.mkString("\n")

            val processList = procs.keys.map { id =>
              val output = outputMap.getOrElse(id, "Waiting for output...")
              s"""<li>Process $id (Running)
                 |<button id="stop-btn-$id" onclick="stopProcess($id)">Stop</button>
                 |<span id="stopped-msg-$id" style="display:none; color:red;">Process stopped</span><br>
                 |<pre id="output-$id">$output</pre>
                 |</li>""".stripMargin
            }.mkString("\n")

            s"""
               |<html>
               |<head>
               |  <title>Process Management</title>
               |  <script>
               |    function startProcess() {
               |      let cmd = prompt("Enter a command:");
               |      if (cmd) {
               |        fetch('/start-process?cmd=' + encodeURIComponent(cmd))
               |          .then(response => response.text())
               |          .then(alert);
               |      }
               |    }
               |
               |    function stopProcess(id) {
               |      fetch('/stop-process?id=' + id)
               |        .then(response => response.text())
               |        .then(alert)
               |        .then(() => {
               |          let stopButton = document.getElementById("stop-btn-" + id);
               |          if (stopButton) {
               |            stopButton.style.display = "none";
               |          }
               |          let stoppedMsg = document.getElementById("stopped-msg-" + id);
               |          if (stoppedMsg) {
               |            stoppedMsg.style.display = "inline";
               |          }
               |        });
               |    }
               |
               |    function updateOutput() {
               |      fetch('/process-output')
               |        .then(response => response.json())
               |        .then(data => {
               |          for (let id in data) {
               |            let outputElement = document.getElementById("output-" + id);
               |            if (outputElement) {
               |              outputElement.innerText = data[id];
               |            }
               |          }
               |        });
               |    }
               |
               |    setInterval(updateOutput, 100);
               |    setTimeout(() => {
               |      location.reload();
               |    }, 5000);
               |  </script>
               |</head>
               |<body>
               |  <h1>Process Control</h1>
               |  <button onclick="startProcess()">Start New Process</button>
               |  <ul>$processList</ul>
               |  <h2>Queue</h2>
               |  <ul>$queueList</ul>
               |</body>
               |</html>
               |""".stripMargin
          }.flatMap(html => Ok(html).map(_.withContentType(org.http4s.headers.`Content-Type`(MediaType.text.html))))
        }
      }
  }

  private def startProcess(cmd: String, userIp: String, processes: Ref[IO, Map[Int, Process]], outputs: Ref[IO, Map[Int, String]]): IO[Response[IO]] = {
    val processId = processCounter.getAndIncrement()
    IO {
      logger.info(s"🔹 Starting process ($processId) for user $userIp: $cmd")

      val process = Process(cmd).run(ProcessLogger(
        line => outputs.update(outMap => outMap.updated(processId, outMap.getOrElse(processId, "") + line + "\n")).unsafeRunAndForget(),
        err  => outputs.update(outMap => outMap.updated(processId, outMap.getOrElse(processId, "") + "[ERROR] " + err + "\n")).unsafeRunAndForget()
      ))

      process
    }.flatMap { process =>
      processes.update(_ + (processId -> process)) *>
        IO(process.exitValue()).attempt.flatMap {
          case Right(_) =>
            IO.sleep(10.seconds) *> {
              processes.update(_ - processId) *> checkQueue(processes, outputs)
            }
          case Left(_) => IO.unit
        }.start *>
        Ok(s"✅ Process started! ID: $processId | Command: $cmd | User: $userIp")
    }
  }

  private def checkQueue(processes: Ref[IO, Map[Int, Process]], outputs: Ref[IO, Map[Int, String]]): IO[Unit] = {
    processes.get.flatMap { procs =>
      if (procs.size < maxProcesses) {
        IO.blocking(Option(pendingQueue.poll(1, TimeUnit.SECONDS))).flatMap {
          case Some((ip, cmd)) =>
            startProcess(cmd, ip, processes, outputs).void
          case None => IO.unit
        }
      } else {
        IO.unit
      }
    }
  }

  def addCORSHeaders(response: Response[IO]): Response[IO] = {
    response.putHeaders(
      Header("Access-Control-Allow-Origin", "*"),
      Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS"),
      Header("Access-Control-Allow-Headers", "Content-Type, Authorization"),
      Header("Access-Control-Allow-Credentials", "true")
    )
  }
}


