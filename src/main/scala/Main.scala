import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.convert.decorateAsScala._
import scala.collection._
import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

object Simulator {

  //setting the logger
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  //get finger size from resources
  val fingerSize = Utilities.getFingerSize("input.txt")

  //calculates the chord size from the given finger size
  val chordSize = Utilities.getChordSize(fingerSize)

  //setting the path reference
  val pathPrefix = "akka://" + "chord-system" + "/user/"

  var nodesInChord: Int = 0;
  var flag: AtomicBoolean = new AtomicBoolean(true)

  //loading movies data set

  var count = 0
  var inspec: AtomicBoolean = new AtomicBoolean(true)
  var TotalHops = 0;
  var movies_count = 0
  val loader = Thread.currentThread.getContextClassLoader
  val is = loader.getResourceAsStream("movies.csv")
  val initialStocks = scala.io.Source.fromInputStream(is).mkString
  val movies_list = initialStocks.split("\n").map(_.trim).drop(0).toList

  var keyToNode = new TrieMap[Int, Int]()
  var keyToMovies = new TrieMap[Int, String]()

  //number of nodes to be placed in the chord ring
  val numberOfNodes = 5

  //number of request to process
  val numberOfRequests = 3

  def main(args: Array[String]): Unit = {

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

    //insertion of keys to node
    for (i <- 1 to numberOfNodes) {
      println("Lookup for Node : " + i)
      var nodeName = Utilities.mkHash(i.toString, Simulator.chordSize)
      var node = system.actorSelection(pathPrefix + nodeName)
      node ! new insertKey(numberOfRequests);
    }

    Thread.sleep(100)

    //prints the finger table of each node joined in the ring
    for (i <- 1 to numberOfNodes) {
      Thread.sleep(10)
      val nodeName = Utilities.mkHash(i.toString, Simulator.chordSize)
      val node = system.actorSelection(pathPrefix + nodeName)
      node ! printTable()
      Thread.sleep(10)
    }

    // time delay
    Utilities.buffer()
    Thread.sleep(10000)

    println("Number of Nodes: " + numberOfNodes)
    println("Number of Requests: " + numberOfRequests)
    println("Keys Searched: " + count)
    println("Number of hops: " + TotalHops)
    println("Avg. number of hops:" + TotalHops.toDouble / (numberOfNodes * numberOfRequests))
    println("Terminated")

   val associatedNode = Utilities.getNodeForKey("Titanic",keyToNode,keyToMovies)
    println("associatedNode for the movie" +associatedNode)

    System.exit(0)
    sys.exit()
  }
}