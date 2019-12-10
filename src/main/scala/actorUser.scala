
import akka.actor.Actor
import org.slf4j.LoggerFactory

import scala.collection.mutable

object actorUser {
//  def apply(groupId: String, deviceId: String): Behavior[Command] = Behaviors.setup(context => new actorUser(context))

  sealed trait Command
  final case class read(key : String) extends Command
  final case class write(key : String) extends Command
  final case class collect() extends Command
}

class actorUser(name : String) extends Actor {
  import actorUser._
  private val stats = new mutable.HashMap[String, Int]()
  private val logging = LoggerFactory.getLogger("User")

  def read(key : String) : Int = {  // @todo
    1
  }

  def write(key : String) : Int = {  // @todo
    2
  }

  def collect() : mutable.HashMap[String, Int] = {
    stats
  }

  def receive: PartialFunction[Any, Unit] = {
    case read(key) => read(key)
    case write(key) => write(key)    case collect() => collect()
  }
}
