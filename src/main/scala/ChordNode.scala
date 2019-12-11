import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.Actor
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

sealed trait Algorithms
case class joinNode(nodeID: Int) extends Algorithms
case class fixFinger(node: Int, i: Int) extends Algorithms
case class updateFingers() extends Algorithms
case class set_predecessor(name: Int, newNode: Boolean) extends Algorithms
case class search_predecessor(key: Int, origin: Int, reqType: String, data: String) extends Algorithms
case class get_successor() extends Algorithms
case class set_successor(name: Int, newNode: Boolean) extends Algorithms
case class search_successor(key: Int, current: Int, i: Int) extends Algorithms
case class printTable() extends Algorithms
case class insertKey(numRequests: Int) extends Algorithms
case class addKeyToNode(keyHash: Int) extends Algorithms
case class getKeyFromNode(keyHash: Int) extends Algorithms
case class nodeCollect() extends Algorithms

class ChordNode(val nodeID: Int, val fingerSize: Int) extends Actor {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  var fingerTable : mutable.Map[Int,Int] = collection.mutable.Map[Int, Int]()
  var successor: Int = -1
  var predecessor: Int = -1
  var nodeInRing: Boolean = false
  var hopCount = 0
  val keyData = new ConcurrentHashMap[Int, String]()

  val countReadTotal = new AtomicInteger(0)
  val countReadFound = new AtomicInteger(0)
  val countReadNotFound = new AtomicInteger(0)
  val countReadFailure = new AtomicInteger(0)
  val countWriteTotal = new AtomicInteger(0)
  val countWriteSuccess = new AtomicInteger(0)
  val countWriteFail = new AtomicInteger(0)
  val totalRequests = new AtomicInteger(0)
  val totalHopCount = new AtomicInteger(0)

  def receive = {
    // Collect Hop Counts from Nodes
    /**
     * When the first node joins the network, its successor and predecessor is set to itself.
     * The entries of the finger table will be initially pointed to itself.
     * For the next incoming nodes, the successor and predecessor is set before making its entries into the finger table.
     */
    case joinNode(randNode: Int) => {
      if (randNode == -1) {
        //incrementing the node count
        Simulator.nodesInChord = Simulator.nodesInChord + 1
        //setting the successor
        successor = nodeID
        logger.debug("First Node Successor" + successor )
        //setting the predecessor
        predecessor = nodeID
        logger.debug("First Node predecessor" + predecessor )
        //constructing the initial finger table
        fingerTable = Utilities.initFingerTable(-1, nodeID, fingerSize)
        logger.debug("Initial Finger Table" + fingerTable )
        nodeInRing = true
      } else {
        val transActor = context.actorSelection(Simulator.pathPrefix + randNode)
        transActor ! search_predecessor(nodeID, nodeID, "setRequest", null)
      }
    }

    /**
     *After setting the node's successor and predecessor, it calls this fixfinger function to set the entries
     * of the finger table. The following formula is applied to make entries into the finger table.
     * ( N + 2 pow i ) mod (2 pow finger size).
     */
    case fixFinger( node: Int, i: Int) => {
      if (i <= fingerSize) {
        val nextIndex: Int = (nodeID + Math.pow(2, i+1).toInt) % Utilities.getChordSize(fingerSize)
        logger.debug("Next Index in Finger Table" + nextIndex)
        Utilities.buildFinger(this, node, i, nextIndex, fingerSize :Int)
      }
    }
    /**
     * This will update the reference in other predecessors of the current node who can possibly point to this node.
     */
    case updateFingers() => {
      val updatableNodes = Utilities.updateFingerTable(nodeID, fingerSize)
      logger.debug("Updated table" + updatableNodes)
      for (i <- 0 until fingerSize) {
        if (!checkPredecessor(updatableNodes.get(i).get)) {
          context.actorSelection(Simulator.pathPrefix + predecessor) ! search_predecessor(updatableNodes(i), nodeID, "fingerUpdate", "" + i)
        }
      }
      nodeInRing = true;
      Simulator.nodesInChord = Simulator.nodesInChord + 1;
      val temp = Simulator.flag.get()
      Simulator.flag.set(!temp)
    }
    /**
     * setSuccessor: Sets the successor variable as the passed node.
     * If the node is a new node and already present in the Chord ring, it will be the successor to itself.
     * If the node is not a new node and not already present in the Chord ring, we update and set the successor.
     */
    case set_successor(name: Int, newNode: Boolean) => {
      logger.debug("setSuccessor\tFrom:" + sender.path)
      if (newNode && nodeInRing) {
        successor = name
        context.actorSelection(Simulator.pathPrefix + successor) ! set_predecessor(name, nodeInRing)
        logger.debug("New Successor: "+ successor)
      } else if (!newNode && !nodeInRing) {
        successor = name
        context.actorSelection(Simulator.pathPrefix + predecessor) ! set_successor(nodeID, !nodeInRing)
        logger.debug("New Successor: "+ successor)
        fingerTable(0) = successor
        self.tell(fixFinger(fingerTable(0), 0), self)
      }
    }

    /**
     * getSuccessor: We set the successor to the sender node.
     */
    case get_successor() => {
      logger.debug("GetSuccessor: "+sender.path)
      sender ! set_successor(successor, false)
    }

    /**
     * setPredecessor: Sets the Predecessor variable as the passed node.
     * If the node is a new node and already present in the Chord ring, it will be the predecessor to itself.
     * If the node is not a new node and not already present in the Chord ring, we update and set the predecessor.
     */
    case set_predecessor(name: Int, newNode: Boolean) => {
      logger.debug("setPredecessor\tFrom:" + sender.path)
      if (nodeInRing && newNode) {
        predecessor = name
        logger.debug("New Predecessor: "+ predecessor)
      } else if (!nodeInRing && ! newNode) {
        predecessor = name
        logger.debug("New Predecessor: "+ predecessor)
        val act = context.actorSelection(Simulator.pathPrefix + predecessor)
        if(!nodeInRing)
          act ! get_successor()
      }
    }

    /**
     * searchPredecessor: To find the predecessor node of the key.
     * If the predecessor is not found we pass on the key to the closest preceding neighbor node.
     */
    case pre: search_predecessor => {
      logger.debug("Search Predecessor\tFrom:" + sender.path)
      val cases = Utilities.getCases(nodeID,successor,pre.key)
      logger.debug("Conditions for checking the Predecessor: "+ cases)
      val nearestNbr = closestPrecedingFinger(pre.key)
      var act = context.actorSelection(Simulator.pathPrefix + pre.origin)

      if (pre.reqType.equals("setRequest")) {
        logger.debug("The finger request is "+ pre.reqType)
        Utilities.findPredSetRequest(this,cases,act,pre,nearestNbr)
      } else if (pre.reqType.equals("fingerUpdate")) {
        logger.debug("The finger request is "+ pre.reqType)
        if (List(cases._1, cases._2, cases._3).reduce(_||_)){
          if (List(fingerTable(pre.data.toInt) > pre.origin , List(fingerTable(pre.data.toInt) < pre.origin , fingerTable(pre.data.toInt) <= nodeID).reduce(_&&_)).reduce(_||_)) {
            logger.info("This is the predecessor of the key")
            fingerTable(pre.data.toInt) = pre.origin
          }
        } else {
          act = null
          if (checkSuccessor(pre.key)) {
            logger.debug("The current predecessor is the predecessor for the key.")
            Utilities.tempActPre(transActor = context.actorSelection(Simulator.pathPrefix + predecessor), pre, nodeID, true, true)
          } else {
            Utilities.tempActPre(transActor = context.actorSelection(Simulator.pathPrefix + fingerTable(nearestNbr)) , pre, nodeID, true, true)
          }
        }
      }
    }

    /**
     * searchSuccessor: To find the successor node of the key.
     * If the successor is not found we pass on the key to the closest preceding neighbor node.
     */
    case search_successor(key: Int, current: Int, i: Int) => {
      logger.debug("Search Successor\tFrom:" + sender.path + "\tKey:" + key)
      val cases = Utilities.getCases(nodeID,successor,key)
      logger.debug("Conditions for checking the successor: "+ cases)
      var nodeGreaterThanPredecesoor = false
      if (predecessor < nodeID)
        nodeGreaterThanPredecesoor = List(predecessor < key , key < nodeID).reduce(_&&_)
      else
        nodeGreaterThanPredecesoor = Utilities.checkGreaterCondition(predecessor, key, nodeID)
      logger.debug("Condition if the node is the successor? "+nodeGreaterThanPredecesoor)

      val transActor = context.actorSelection(Simulator.pathPrefix + current)
      val nearestNeighbour = closestPrecedingFinger(key)

      if (List(cases._1, cases._2, cases._3).reduce(_||_)){
        logger.info("The current successor is the successor of the given key")
        Utilities.tempActSucc(transActor, key, successor, i, false)
      } else if (nodeGreaterThanPredecesoor) {
        Utilities.tempActSucc(transActor,key, nodeID, i, false)
      } else {
        logger.info("Pass the key to the closest preceeding node")
        if (fingerTable(nearestNeighbour) == nodeID) {
          Utilities.tempActSucc(transActor, key, fingerTable(nearestNeighbour), i, false)
        } else {
          Utilities.tempActSucc(transActor = context.actorSelection(Simulator.pathPrefix + fingerTable(nearestNeighbour)), key, current, i, true)
        }
      }
    }
    case printTable() => {
      Utilities.printFingerTable(this)
    }

//    case insertKey(numRequests: Int) => {
//      for (i <- 1 to numRequests) {
//        val Key = Utilities.mkHash(Simulator.pathPrefix + nodeID + i, Simulator.chordSize)
//        Simulator.keyToMovies(Key) = Simulator.movies_list(Simulator.movies_count)
//        Simulator.movies_count = Simulator.movies_count + 1
//        Thread.sleep(10)
//        self ! addKeyToNode(Key)
//      }
//    }

    case addKeyToNode(keyHash: Int) => {
      totalRequests.incrementAndGet()
      findOrAdd(keyHash, true)
    }

    case getKeyFromNode(keyHash : Int) => {
      totalRequests.incrementAndGet()
      findOrAdd(keyHash, false)
    }

    case nodeCollect() => {
      sender ! Map(
        "countReadTotal" -> countReadTotal.get,
        "countReadFound" -> countReadFound.get,
        "countReadNotFound" -> countReadNotFound.get,
//        "countReadFailure" -> countReadFailure.get,
        "countWriteTotal" -> countWriteTotal.get,
//        "countWriteSuccess" -> countWriteSuccess.get,
//        "countWriteFail" -> countWriteFail.get,
        "totalRequests" -> totalRequests.get,
        "totalHopCount" -> totalHopCount.get
      )

      countReadTotal.set(0)
      countReadFound.set(0)
      countReadNotFound.set(0)
      countReadFailure.set(0)
      countWriteTotal.set(0)
      countWriteSuccess.set(0)
      countWriteFail.set(0)
      totalRequests.set(0)
      totalHopCount.set(0)
    }
  }

  def checkSuccessor(key: Int): Boolean = {

    if ((predecessor <= key) && (predecessor < nodeID))
      (key < nodeID)
    else
      Utilities.checkGreaterCondition(predecessor, key, nodeID)

  }

  def checkPredecessor(key: Int): Boolean = {

    if ((key <= successor) && (successor > nodeID))
      (key > nodeID)
    else
      Utilities.checkGreaterCondition(nodeID, key, successor)
  }

  def closestPrecedingFinger(key: Int): Int = {
    var keyFound = 2147483647
    var lowerBound = 2147483647
    var maxBound = 2147483647
    var positiveValFound = false

    for (i <- 0 until fingerTable.size)
    {
      var diff = key - fingerTable(i)
      if (0 < diff && diff < maxBound)
      {
        keyFound = i;
        maxBound = diff
        positiveValFound = true
      } else if (diff < 0 && diff < lowerBound && !positiveValFound)
      {
        keyFound = i;
        lowerBound = diff
      }
    }
    keyFound
  }

  private def findOrAdd(keyHash : Int, isAdd : Boolean): Unit = {
    if (keyHash == 2147483647) {
      return
    }

    totalHopCount.incrementAndGet()

    var successorCheck = 0
    var predecessorCheck = 0

    //checks the successor node
    if (checkSuccessor(keyHash)) {
      if (isAdd) {
        // add key
        keyData.replace(keyHash, "movie sign")

        countWriteTotal.incrementAndGet()
      } else {
        // reads
        countReadTotal.incrementAndGet()

        if (keyData.get(keyHash) == null) {
          // not found
          countReadNotFound.incrementAndGet()
        } else {
          // found
          countReadFound.incrementAndGet()
        }
      }

      successorCheck = 1
    }
    //if successsor not found, checks the predeccessor
    if(successorCheck == 0){
      if(checkPredecessor(keyHash)){
        context.actorSelection(Simulator.pathPrefix + successor) ! (if (isAdd) addKeyToNode(keyHash) else getKeyFromNode(keyHash))
        predecessorCheck = 1
      }
      //if both successor and predeccessor not found
      else if (predecessorCheck == 0){
        context.actorSelection(Simulator.pathPrefix + fingerTable(closestPrecedingFinger(keyHash))) ! (if (isAdd) addKeyToNode(keyHash) else getKeyFromNode(keyHash))
      }
    }
  }
}