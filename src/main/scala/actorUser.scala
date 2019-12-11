import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import ChordSimulatorService.system
import akka.actor.{Actor, ActorRef}
import akka.util.Timeout
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

sealed trait UserCommands
final case class read(key : String, node : ActorRef) extends UserCommands
final case class write(key : String, node : ActorRef) extends UserCommands
final case class collect() extends UserCommands

class actorUser(name : String, fingerSize : Int) extends Actor {
  private val stats = new ConcurrentHashMap[String, AtomicLong]()
  private val logging = LoggerFactory.getLogger("User")
  private val chordSize = Utilities.getChordSize(fingerSize)
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  def doWrite(data : String, node : ActorRef) : Unit = {
    implicit val timeout: Timeout = Timeout(1 second)
    val dataHash = Utilities.mkHash(data, chordSize)
    node ! addKeyToNode(dataHash)
  }

  def doRead(key : String, node : ActorRef) : Unit = {  // @todo
    addToStatsCounter("WRITE-SUCCESS")
  }

  def doCollect() : ConcurrentHashMap[String, AtomicLong] = {
    stats
  }

  def receive: PartialFunction[Any, Unit] = {
    case read(key, node) => sender() ! doRead(key, node)
    case write(key, node) => sender() ! doWrite(key, node)
    case collect() => sender() ! doCollect()
    case _ => logging.info("Received unknown message")
  }

  private def addToStatsCounter(key : String) : Long = {
    stats.putIfAbsent(key, new AtomicLong(0L))
    stats.get(key).incrementAndGet()
  }
}
