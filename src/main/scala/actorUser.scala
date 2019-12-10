
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import ChordSimulatorService.system
import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success}

sealed trait UserCommands
final case class read(key : String) extends UserCommands
final case class write(key : String) extends UserCommands
final case class collect() extends UserCommands

class actorUser(name : String, fingerSize : Int) extends Actor {
  private val stats = new ConcurrentHashMap[String, AtomicLong]()
  private val logging = LoggerFactory.getLogger("User")
  private val chordSize = Utilities.getChordSize(fingerSize)
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  def doWrite(data : String) : Unit = {
    implicit val timeout: Timeout = Timeout(1 second)
    val dataHash = Utilities.mkHash(data, chordSize)

    ask(self, findKey(dataHash)).onComplete {
      case Success(v) =>
        logging.debug("Adding data to found node")

        ask(context.actorSelection("akka://chord-simulation/users/"+v), addData(dataHash)).onComplete {
          case Success(v) =>
            logging.debug("Data successfully added - " + v)
            addToStatsCounter("WRITE-SUCCESS")

          case Failure(e) =>
            logging.info("Unable to add data - " + e.getMessage)
            addToStatsCounter("WRITE-FAILURE")
        }

      case Failure(e) =>
        logging.info("Error looking up key for data - "+e.getMessage)
        addToStatsCounter("WRITE-FAILURE")
    }
  }

  def doRead(key : String) : Unit = {  // @todo
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
