
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.Actor

class ChordNode(val hashName: Int, val abstractName: String, val requests: Int) extends Actor {

  var finger1 = collection.mutable.Map[Int, Int]()

  var successor: Int = -1;
  var predecessor: Int = -1;
  var isJoined: Boolean = false;

  val request = "fingerRequest"
  val setRequest = "setRequest"
  val fingerUpdate = "fingerUpdate"

  val prefix = "akka://" + "system" + "/user/"


  def receive = {

    case Messages.join(randNode: Int) => {

      if (randNode == -1) {
        successor = hashName;
        predecessor = hashName;
        finger1 = Utilities.initFingerTable(-1, hashName,Simulator.fingerSize);
        isJoined = true
        Simulator.numNodesJoined = Simulator.numNodesJoined + 1;
      } else {
        //tempactor always stores the first node
        var tempActor = context.actorSelection(prefix + randNode)
        tempActor ! Messages.findPredecessor(hashName, hashName, setRequest, null)
      }
    }

    case Messages.fixfinger( node: Int, i: Int) => {
      if (i <= Simulator.fingerSize) {
        val nextIndex: Int = (hashName + Math.pow(2, i).toInt) % Simulator.chordSize
        Utilities.fixFinger(this, node, i, nextIndex, Simulator.fingerSize  :Int)
      }
    }

    case Messages.updateFingers() => {
      val updatableNodes = Utilities.updateFingerTable(hashName,Simulator.fingerSize)
      for (i <- 0 until Simulator.fingerSize) {
        if (!checkPredecessor(updatableNodes.get(i).get)) {
          context.actorSelection(prefix + predecessor) ! Messages.findPredecessor(updatableNodes(i), hashName, fingerUpdate, "" + i)
        }
      }
      isJoined = true;
      Simulator.numNodesJoined = Simulator.numNodesJoined + 1;
      var temp = Simulator.checker.get()
      Simulator.checker.set(!temp)
    }


    case Messages.getSuccessor(isNewNode: Boolean) => {
      if (isNewNode) {
        sender ! Messages.setSuccessor(successor, false)
      }
    }

    case Messages.setSuccessor(name: Int, isNewNode: Boolean) => {
      if (isNewNode && isJoined) {
        var myOldSuccesor = context.actorSelection(prefix + successor)
        successor = name
        myOldSuccesor ! Messages.setPredecessor(name, isJoined)
      } else if (!isNewNode && !isJoined) {
        successor = name
        var myNewPredecessor = context.actorSelection(prefix + predecessor)
        myNewPredecessor ! Messages.setSuccessor(hashName, !isJoined)
        var f = Future {
          Thread.sleep(10)
        }
        f.onComplete {
          case x =>
            finger1.update(0, successor)
            self.tell(Messages.fixfinger(finger1.get(0).get, 0), self)
        }
      }
    }

    case Messages.setPredecessor(name: Int, isNewNode: Boolean) => {
      if (isJoined && isNewNode) {
        predecessor = name;
      } else if (!isJoined && ! isNewNode) {
        predecessor = name
        var act = context.actorSelection(prefix + predecessor)
        act ! Messages.getSuccessor(!isJoined)
      }
    }

    case f: Messages.findPredecessor => {
      var start = hashName
      var end = successor
      var condition1 = (start == end)
      var condition2 = (end > start && (f.key >= start && f.key < end))
      var condition3 = (end < start && ((f.key >= start && f.key < Simulator.chordSize) || (f.key >= 0 && f.key < end)))

      if (f.reqType.equals(setRequest)) {
        if (condition1 || condition2 || condition3) {
          var act = context.actorSelection(prefix + f.origin)
          act ! Messages.setPredecessor(start, false)
        } else {
          var nearestNbr = closestPrecedingFinger(f.key);
          if (finger1.get(nearestNbr).get == hashName) {
            var act = context.actorSelection(prefix + f.origin)
            act ! Messages.setPredecessor(start, false)
          } else {
            var act = context.actorSelection(prefix + finger1.get(nearestNbr).get)
            act ! f
          }
        }
      } else if (f.reqType.equals(fingerUpdate)) {
        if (condition1 || condition2 || condition3) {
          var c1 = (finger1.get(f.data.toInt).get > f.origin)
          var c2 = ((finger1.get(f.data.toInt).get < f.origin) && (finger1.get(f.data.toInt).get <= hashName))
          if (c1 || c2) {
            finger1.update(f.data.toInt, f.origin)
          }
        } else {
          var act = null
          if (checkSuccessor(f.key)) {
            var act = context.actorSelection(prefix + predecessor)
            act ! f
          } else {
            var closetNeigh = closestPrecedingFinger(f.key);
            var act = context.actorSelection(prefix + finger1.get(closetNeigh).get)
            act ! f
          }
        }
      }
    }

    case Messages.findSuccessor(key: Int, origin: Int, reqType: String, i: Int) => {
      if (reqType.equals(request)) {

        var start = hashName
        var end = successor

        var condition1 = (start == end)
        var condition2 = (end > start && (key >= start && key < end))
        var condition3 = (end < start && ((key >= start && key < Simulator.chordSize) || (key >= 0 && key < end)))

        var condition4 = false
        if (predecessor < hashName)
          condition4 = (predecessor < key && key < hashName)
        else {
          var c1 = (predecessor < key && key < Simulator.chordSize)
          var c2 = (0 < key && key < hashName)
          condition4 = (c1 && !c2) || (!c1 && c2)
        }

        if (condition1 || condition2 || condition3) {
          var tmpActor = context.actorSelection(prefix + origin)
          tmpActor ! Messages.fixfinger(end, i)
        } else if (condition4) {
          var tmpActor = context.actorSelection(prefix + origin)
          tmpActor ! Messages.fixfinger( hashName, i)
        } else {
          var nearestNeighbour = closestPrecedingFinger(key);
          if (finger1.get(nearestNeighbour).get == hashName) {
            var tmpActor = context.actorSelection(prefix + origin)
            tmpActor ! Messages.fixfinger(finger1.get(nearestNeighbour).get, i)
          } else {
            var tmpActor = context.actorSelection(prefix + finger1.get(nearestNeighbour).get)
            tmpActor ! Messages.findSuccessor(key, origin, reqType, i)
          }
        }
      }
    }

    case Messages.printTable() => {
      Utilities.printFingerTable(this)
    }
  }

  def checkSuccessor(key: Int): Boolean = {
    var checkSucc = false
    if (predecessor < hashName) {
      checkSucc = (predecessor <= key) && (key < hashName)
    } else {
      var condition1 = (predecessor <= key) && (key < Simulator.chordSize)
      var condition2 = (0 < key) && (key < hashName)
      checkSucc = (condition1 && !condition2) || (!condition1 && condition2)
    }
    checkSucc
  }

  def checkPredecessor(key: Int): Boolean = {

    var checkPred = false
    if (successor > hashName) {
      checkPred = (key <= successor) && (key > hashName)
    } else {
      var condition1 = (hashName < key) && (key < Simulator.chordSize)
      var condition2 = (0 < key) && (key < successor)
      checkPred = (condition1 && !condition2) || (!condition1 && condition2)
    }
    checkPred
  }

  def closestPrecedingFinger(key: Int): Int = {
    var keyFound = Integer.MIN_VALUE
    var distantNbr = Integer.MIN_VALUE
    var current = Integer.MIN_VALUE;

    var lowerNbr = Integer.MAX_VALUE
    var higherNbr = Integer.MAX_VALUE
    var positiveValFound = false

    for (i <- 0 until finger1.size) {

      var diff = key - finger1.get(i).get
      if (0 < diff && diff < higherNbr) {
        keyFound = i;
        higherNbr = diff
        positiveValFound = true
      } else if (diff < 0 && diff < lowerNbr && !positiveValFound) {
        keyFound = i;
        lowerNbr = diff
      }
    }
    keyFound
  }
}