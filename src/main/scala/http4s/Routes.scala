package http4s

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io._
import scala.jdk.CollectionConverters._
import org.http4s.headers.`Content-Type`
import utils.{ProcessManager, ServerStatus}

object Routes {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "run-process" =>
      val userCmd = req.uri.query.params.get("cmd")
      val userToken = req.uri.query.params.getOrElse("token", "unknown")

      userCmd match {
        case Some(cmd) => ProcessManager.waitAndRunProcess(userToken, cmd).map(addCORSHeaders)
        case None => BadRequest("ERROR: Command not provided. Use /start-process?cmd=<your_command>&token=<your_token>").map(addCORSHeaders)
      }

    case POST -> Root / "reset" =>
      ProcessManager.resetAll
      Ok("Server reset completed successfully")

    case GET -> Root / "admin-panel" =>

      val status = s"<pre>${ServerStatus.getStatus()}</pre>"
      val pendingQueue = new StringBuilder
      ProcessManager.getPendingQueue.forEach { case processData =>
        pendingQueue.append(s"<li><b>${processData.cmd}</b> => ${processData.token}</li>\n")
      }
      val historyTable = new StringBuilder
      val historyTableContent = ProcessManager.getProcessHistory.asScala.toList.sortBy(_.id).map {
        case processData =>
          val id = processData.id
          val token = processData.token
          val cmd = processData.cmd
          s"<tr><td>$id</td><td>$cmd</td><td>$token</td></tr>"
      }.mkString(
        """
          |<div class="historyTable">
          |<h2>Command History</h2>
          |<table border="1" cellpadding="8" cellspacing="0" style="border-collapse: collapse; width: 100%; background: #fff;">
          |<thead style="background-color: #f0f0f0;"><tr><th>ID</th><th>Command</th><th>User Token</th></tr></thead>
          |<tbody>
        """.stripMargin,
        "",
        "</tbody></table>"
      )

      val htmlPage = s"""
         |<html>
         |<head>
         |  <title>Admin Panel</title>
         |  <style>
         |
         |  h1, h2 {
         |    text-align: center;
         |  }
         |
         |  button {
         |    background-color: #3498db;
         |    color: white;
         |    border: none;
         |    padding: 8px 12px;
         |    border-radius: 4px;
         |    font-size: 14px;
         |    cursor: pointer;
         |    margin-left: 10px;
         |  }
         |
         |  li {
         |    padding: 0.2em;
         |    max-width: 600px;
         |    max-height: 125px;
         |    overflow-y: auto;
         |  }
         |
         |  .status {
         |  padding-left:2em
         |  }
         |
         |  .queueList {
         |    max-width: 600px;
         |    max-height: 600px;
         |    overflow-y: auto;
         |  }
         |
         |  .historyTable {
         |    position: fixed;
         |    right: 5%;
         |    top: 10%;
         |    width: 1000px;
         |    max-width: 100%;
         |    max-height: 600px;
         |    overflow-y: auto;
         |  }
         |</style>
         |  <script>
         |    function startProcess() {
         |      let cmd = prompt("Enter a command:");
         |      if (!cmd) return;
         |      const url = '/run-process?cmd=' + encodeURIComponent(cmd) + '&token=admin';
         |      fetch(url)
         |        .then(response => response.text())
         |        .then(alert)
         |        .catch(err => alert("Failed to start process: " + err));
         |    }
         |
         |    function reset() {
         |      if (confirm("Are you sure you want to reset the server?")) {
         |        fetch('/reset', { method: 'POST' })
         |          .then(response => response.text())
         |          .then(alert)
         |          .then(() => location.reload())
         |          .catch(err => alert("Reset failed: " + err));
         |      }
         |    }
         |  </script>
         |</head>
         |<body>
         |  <h1>Admin Panel</h1>
         |  <button onclick="startProcess()">Start New Process</button>
         |  <button onclick="reset()">Reset</button>
         |  <div class="status">
         |  <h3>Server Status</h3>
         |  $status
         |  </div>
         |  <div class="queueList">
         |  <h2>Queue</h2>
         |  <ul>$pendingQueue</ul>
         |  </div>
         |  $historyTableContent
         |</body>
         |</html>
         |""".stripMargin
      
      Ok(htmlPage).map(_.withContentType(`Content-Type`(MediaType.text.html)))
  }

  def addCORSHeaders(response: Response[IO]): Response[IO] = {
    response.putHeaders(
      "Access-Control-Allow-Origin" -> "*",
      "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
      "Access-Control-Allow-Headers" -> "Content-Type, Authorization",
      "Access-Control-Allow-Credentials" -> "true",
    )
  }
}
