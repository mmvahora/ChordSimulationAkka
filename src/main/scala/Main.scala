import java.util.concurrent.atomic.AtomicBoolean
import akka.actor.{ActorRef, ActorSystem, Props}
import org.slf4j.{Logger, LoggerFactory}

object Simulator {

  //setting the logger
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  //get finger size from resources
  val fingerSize = Utilities.getFingerSize("input.txt")

  //calculates the chord size from the given finger size
  val chordSize = Utilities.getChordSize(fingerSize)

  //setting the path reference
  val pathPrefix = "akka://" + "system" + "/user/"

  var nodesInChord: Int = 0;
  var flag: AtomicBoolean = new AtomicBoolean(true)

  def main(args: Array[String]): Unit = {

    //number of nodes to be placed in the chord ring
    val numberOfNodes = 10

    //number of request to process
    val numberOfRequests = 5

    //actor system
    val system = ActorSystem("system")

    //initializing the node and actor
    var initNode: Int = -1
    var node: ActorRef = null

    //nodes joining the chord ring one by one
    for (i <- 1 to numberOfNodes) {
      if (initNode == -1) {
        //get initial node
        initNode = Utilities.mkHash(i.toString, Simulator.chordSize)
        logger.debug("Hash Id for the initial node" + initNode )
        //setting actor properties
        node = system.actorOf(Props(new ChordNode(initNode)), initNode.toString)
        node ! joinNode(-1)
        Thread.sleep(1000)
      }
      else {
        val temp = flag.get()
        //next node to join
        val nextNode = Utilities.mkHash(i.toString, Simulator.chordSize)
        logger.debug("Hash Id for the next available node" + initNode )
        //setting the actor properties
        val node = system.actorOf(Props(new ChordNode(nextNode)), nextNode.toString)
        node ! joinNode(initNode)
        while (temp == flag.get && nodesInChord < numberOfNodes) {
          //sleeps till the node successfully joins the chord ring
          Thread.sleep(1)
        }
      }
    }
    //prints the finger table of each node joined in the ring
    for (i <- 1 to numberOfNodes) {
      Thread.sleep(10)
      val nodeName = Utilities.mkHash(i.toString, Simulator.chordSize)
      val node = system.actorSelection(pathPrefix + nodeName)
      node ! printTable()
      Thread.sleep(10)
    }
    sys.exit()
  }
}