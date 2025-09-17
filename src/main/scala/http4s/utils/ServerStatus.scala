package http4s.utils

import java.lang.management.{ManagementFactory, OperatingSystemMXBean, RuntimeMXBean}

object ServerStatus {

  private var uptime = getUptime()
  private var memoryUsage = getMemoryUsage()
  private var threadCount = getThreadCount()
  private var systemLoad = getSystemLoad()
  private var queueSize = getQueueSize()

  def getUptime(): String = {
    val runtimeMXBean: RuntimeMXBean = ManagementFactory.getRuntimeMXBean
    val uptimeMillis = runtimeMXBean.getUptime
    val uptimeSeconds = uptimeMillis / 1000
    val uptimeMinutes = uptimeSeconds / 60
    val uptimeHours = uptimeMinutes / 60
    s"$uptimeHours hours, ${uptimeMinutes % 60} minutes, ${uptimeSeconds % 60} seconds"
  }

  def getMemoryUsage(): String = {
    val runtime = Runtime.getRuntime
    val totalMemory = runtime.totalMemory() / (1024 * 1024)
    val freeMemory = runtime.freeMemory() / (1024 * 1024)
    val usedMemory = totalMemory - freeMemory
    s"Used memory: $usedMemory MB"
  }

  def getThreadCount(): Int = {
    val threadMXBean = ManagementFactory.getThreadMXBean
    threadMXBean.getThreadCount
  }

  def getSystemLoad(): String = {
    val osMXBean: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean
    val systemLoad = osMXBean.getSystemLoadAverage
    if (systemLoad < 0) "N/A" else f"$systemLoad%.2f"
  }

  def getQueueSize(): Int = {
    ProcessManager.getPendingQueue.size()
  }

  def updateStatus(): Unit = {
    uptime = getUptime()
    memoryUsage = getMemoryUsage()
    threadCount = getThreadCount()
    systemLoad = getSystemLoad()
    queueSize = getQueueSize()
  }

  def getStatus(): String = {
    updateStatus()
    s"""
       |Uptime: $uptime
       |Memory Usage: $memoryUsage
       |Thread Count: $threadCount
       |System Load: $systemLoad
       |Queue Size: $queueSize
     """.stripMargin
  }
}
