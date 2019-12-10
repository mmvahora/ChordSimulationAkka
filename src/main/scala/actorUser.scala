
import java.util.concurrent.ConcurrentHashMap

import akka.actor.Actor
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

sealed trait UserCommands
final case class read(key : String) extends UserCommands
final case class write(key : String) extends UserCommands
final case class collect() extends UserCommands

class actorUser(name : String) extends Actor {
  private val stats = new ConcurrentHashMap[String, Int]()
  private val logging = LoggerFactory.getLogger("User")

  def doRead(key : String) : Int = {  // @todo
    1
  }

  def doWrite(key : String) : Int = {  // @todo
    2
  }

  def doCollect() : Map[String, Int] = {
    val jH = new java.util.HashMap[String, Int](stats)
    jH.asScala.toMap
  }

  def receive: PartialFunction[Any, Unit] = {
    case read(key) => sender() ! doRead(key)
    case write(key) => sender() ! doWrite(key)
    case collect() => sender() ! doCollect()
    case _ => logging.info("Received unknown message")
  }
}
