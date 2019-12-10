
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import akka.actor.Actor
import org.slf4j.LoggerFactory

sealed trait UserCommands
final case class read(key : String) extends UserCommands
final case class write(key : String) extends UserCommands
final case class collect() extends UserCommands

class actorUser(name : String) extends Actor {
  private val stats = new ConcurrentHashMap[String, AtomicLong]()
  private val logging = LoggerFactory.getLogger("User")

  def doRead(key : String) : Unit = {  // @todo
    addToStatsCounter("READ-SUCCESS")
  }

  def doWrite(key : String) : Unit = {  // @todo
    addToStatsCounter("WRITE-SUCCESS")
  }

  def doCollect() : ConcurrentHashMap[String, AtomicLong] = {
    stats
  }

  def receive: PartialFunction[Any, Unit] = {
    case read(key) => sender() ! doRead(key)
    case write(key) => sender() ! doWrite(key)
    case collect() => sender() ! doCollect()
    case _ => logging.info("Received unknown message")
  }

  private def addToStatsCounter(key : String) : Long = {
    stats.putIfAbsent(key, new AtomicLong(0L))
    stats.get(key).incrementAndGet()
  }
}
