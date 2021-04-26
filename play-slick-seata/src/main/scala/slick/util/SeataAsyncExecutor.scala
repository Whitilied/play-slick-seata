package slick.util

import io.seata.core.context.RootContext
import slick.util.AsyncExecutor.{PrioritizedRunnable, Priority, WithConnection}

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{Duration, _}

object SeataAsyncExecutor extends Logging {


  def default(name: String = "AsyncSeataExecutor.default") = apply(name, 20, 20, 20)

  def apply(name: String, minThreads: Int, maxThreads: Int, queueSize: Int, maxConnections: Int = Integer.MAX_VALUE, keepAliveTime: Duration = 1.minute) = new AsyncExecutor {
    logger.debug("SeataAsyncExecutor.apply")
    // Before init: 0, during init: 1, after init: 2, during/after shutdown: 3
    private[this] val state = new AtomicInteger(0)
    @volatile private[this] var executor: ThreadPoolExecutor = _

    if (maxConnections > maxThreads) {
      // NOTE: when using transactions or DB locks, it may happen that a task has a lock on the database but no thread
      // to complete its action, while other tasks may have all the threads but are waiting for the first task to
      // complete. This creates a deadlock.
      logger.warn("Having maxConnection > maxThreads can result in deadlocks if transactions or database locks are used.")
    }

    lazy val executionContext: ExecutionContextExecutor = {
      if (!state.compareAndSet(0, 1))
        throw new IllegalStateException("Cannot initialize ExecutionContext; AsyncExecutor already shut down")
      val queue: BlockingQueue[Runnable] = queueSize match {
        case 0 =>
          // NOTE: SynchronousQueue does not schedule high-priority tasks before others and so it cannot be used when
          // the number of connections is limited (lest high-priority tasks may be holding all connections and low/mid
          // priority tasks all threads -- resulting in a deadlock).
          require(maxConnections == Integer.MAX_VALUE, "When using queueSize == 0 (direct hand-off), maxConnections must be Integer.MAX_VALUE.")

          new SynchronousQueue[Runnable]
        case -1 =>
          // NOTE: LinkedBlockingQueue does not schedule high-priority tasks before others and so it cannot be used when
          // the number of connections is limited (lest high-priority tasks may be holding all connections and low/mid
          // priority tasks all threads -- resulting in a deadlock).
          require(maxConnections == Integer.MAX_VALUE, "When using queueSize == -1 (unlimited), maxConnections must be Integer.MAX_VALUE.")

          new LinkedBlockingQueue[Runnable]
        case n =>
          // NOTE: The current implementation of ManagedArrayBlockingQueue is flawed. It makes the assumption that all
          // tasks go through the queue (which is responsible for scheduling high-priority tasks first). However, that
          // assumption is wrong since the ThreadPoolExecutor bypasses the queue when it creates new threads. This
          // happens whenever it creates a new thread to run a task, i.e. when minThreads < maxThreads and the number
          // of existing threads is < maxThreads.
          //
          // The only way to prevent problems is to have minThreads == maxThreads when using the
          // ManagedArrayBlockingQueue.
          require(minThreads == maxThreads, "When using queueSize > 0, minThreads == maxThreads is required.")

          // NOTE: The current implementation of ManagedArrayBlockingQueue.increaseInUseCount implicitly `require`s that
          // maxThreads <= maxConnections.
          require(maxThreads <= maxConnections, "When using queueSize > 0, maxThreads <= maxConnections is required.")

          // NOTE: Adding up the above rules
          // - maxThreads >= maxConnections, to prevent database locking issues when using transactions
          // - maxThreads <= maxConnections, required by ManagedArrayBlockingQueue
          // - maxThreads == minThreads, ManagedArrayBlockingQueue
          //
          // We have maxThreads == minThreads == maxConnections as the only working configuration

          new ManagedArrayBlockingQueue(maxConnections, n).asInstanceOf[BlockingQueue[Runnable]]
      }
      val tf = new DaemonThreadFactory(name + "-")
      executor = new ThreadPoolExecutor(minThreads, maxThreads, keepAliveTime.toMillis, TimeUnit.MILLISECONDS, queue, tf) {

        /** If the runnable/task is a low/medium priority item, we increase the items in use count, because first thing it will do
         * is open a Jdbc connection from the pool.
         */
        override def beforeExecute(t: Thread, r: Runnable): Unit = {
          (r, queue) match {
            case (pr: PrioritizedRunnable, q: ManagedArrayBlockingQueue[Runnable]) if pr.priority != WithConnection => q.increaseInUseCount(pr)
            case _ =>
          }
          super.beforeExecute(t, r)
        }

        /**
         * If the runnable/task has released the Jdbc connection we decrease the counter again
         */
        override def afterExecute(r: Runnable, t: Throwable): Unit = {
          super.afterExecute(r, t)
          (r, queue) match {
            case (pr: PrioritizedRunnable, q: ManagedArrayBlockingQueue[Runnable]) if pr.connectionReleased => q.decreaseInUseCount()
            case _ =>
          }
        }

      }
      if (!state.compareAndSet(1, 2)) {
        executor.shutdownNow()
        throw new IllegalStateException("Cannot initialize ExecutionContext; AsyncExecutor shut down during initialization")
      }

      new ExecutionContextExecutor {
        override def reportFailure(t: Throwable): Unit = loggingReporter(t)

        override def execute(command: Runnable): Unit = {
          val xid = RootContext.getXID

          def traceXid[T](r: => T) = {
            if (xid != null) {
              RootContext.bind(xid)
              logger.debug(s"current xid:${xid}")
            }
            r
          }

          executor.execute(new PrioritizedRunnable {

            override val priority: Priority = WithConnection

            override def run(): Unit = {
              traceXid(command.run())
            }
          })
        }
      }
    }

    def close(): Unit = if (state.getAndSet(3) == 2) {
      executor.shutdownNow()
      if (!executor.awaitTermination(30, TimeUnit.SECONDS))
        logger.warn("Abandoning ThreadPoolExecutor (not yet destroyed after 30 seconds)")
    }
  }


  private class DBIOThreadFactory(namePrefix: String) extends ThreadFactory {
    private[this] val group = Option(System.getSecurityManager).fold(Thread.currentThread.getThreadGroup)(_.getThreadGroup)
    private[this] val threadNumber = new AtomicInteger(1)

    def newThread(r: Runnable): Thread = {
      val t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement, 0)
      if (!t.isDaemon) t.setDaemon(true)
      if (t.getPriority != Thread.NORM_PRIORITY) t.setPriority(Thread.NORM_PRIORITY)
      t
    }
  }

  private class DaemonThreadFactory(namePrefix: String) extends ThreadFactory {
    private[this] val group = Option(System.getSecurityManager).fold(Thread.currentThread.getThreadGroup)(_.getThreadGroup)
    private[this] val threadNumber = new AtomicInteger(1)

    def newThread(r: Runnable): Thread = {
      val t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement, 0)
      if (!t.isDaemon) t.setDaemon(true)
      if (t.getPriority != Thread.NORM_PRIORITY) t.setPriority(Thread.NORM_PRIORITY)
      t
    }
  }

  val loggingReporter: Throwable => Unit = (t: Throwable) => {
    logger.warn("Execution of asynchronous I/O action failed", t)
  }

}
