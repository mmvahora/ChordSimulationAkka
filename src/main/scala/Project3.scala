
import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Props
import java.util.concurrent.atomic.AtomicBoolean

object Simulator {

  val namingPrefix = "akka://" + "system" + "/user/"

  var numNodesJoined: Int = 0;
  var checker: AtomicBoolean = new AtomicBoolean(true)

   val fingerSize = Utilities.getFingerSize("input.txt")
   val chordSize = Utilities.getChordSize(fingerSize)

  def main(args: Array[String]): Unit = {

    val numNodes = 10
    val numRequests = 5

    val actor = ActorSystem("system")
    var startNode: Int = -1;
    var node1: ActorRef = null;

    for (i <- 1 to numNodes) {
      if (startNode == -1) {
        startNode = Hashing.getHash(i.toString(), chordSize)
        node1 = actor.actorOf(Props(new ChordNode(startNode, i.toString(), numRequests)), startNode.toString())
        node1 ! new Messages.join(-1)
        Thread.sleep(1000)
      } else {
        var x = checker.get()
        var hashName = Hashing.getHash(i.toString(), chordSize)
        var node = actor.actorOf(Props(new ChordNode(hashName, i.toString(), numRequests)), hashName.toString())
        node ! new Messages.join(startNode)
      }
    }
    Thread.sleep(100)

    for (i <- 1 to numNodes) {
      var hashName = Hashing.getHash(i.toString(), chordSize)
      var node = actor.actorSelection(namingPrefix + hashName)
      node ! new Messages.printTable()
      Thread.sleep(20)
    }
    sys.exit()
  }

}