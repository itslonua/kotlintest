package io.kotlintest

import org.reflections.Reflections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

object Project {

  private fun discoverProjectConfig(): ProjectConfig? {
    return if (System.getProperty("projectConfigScan") == "false") {
      null
    } else {
      ReflectionsHelper.registerUrlTypes()
      val configClasses = Reflections().getSubTypesOf(ProjectConfig::class.java)
      when {
        configClasses.size == 0 -> null
        configClasses.size > 1 -> {
          val configClassNames = configClasses.map { config -> config.simpleName }
          throw InvalidConfigException("Multiple ProjectConfigs found: $configClassNames")
        }
        else -> configClasses.firstOrNull()?.kotlin?.objectInstance
      }
    }
  }

  private var projectConfig = discoverProjectConfig()
  private val executedBefore = AtomicBoolean(false)
  private val completedSpecs = AtomicInteger(0)
  private var testSuiteCount = 0

  init {

  }

  internal fun beforeAll() {
    if (executedBefore.compareAndSet(false, true)) {
      projectConfig?.extensions?.forEach { extension -> extension.beforeAll() }
      projectConfig?.beforeAll()
    }
  }

  internal fun afterAll() {
    // we will start a thread to execute the afterAll logic,
    // if a new test runner (and hence new tests) is created within 5 seconds,
    // the count will bump and this will not run until next time
    // it's a bit hacky, but unaware of how else to keep track of total test suites created
    // a test runner is always quick to create, so the 5 seconds should be enough
    thread {
      Thread.sleep(5000)
      if (completedSpecs.incrementAndGet() == testSuiteCount) {
        projectConfig?.afterAll()
        projectConfig?.extensions?.reversed()?.forEach { extension -> extension.afterAll() }
      }
    }
  }

  fun incrementTestSuiteCount() = testSuiteCount++
}