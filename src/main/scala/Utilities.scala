
object Utilities {

  //gets the finger size from the input file
  def getFingerSize(filename : String): Int = {
    var input_map = collection.Map[String, Float]()
    val loader = Thread.currentThread.getContextClassLoader
    val is = loader.getResourceAsStream(filename)
    val initialStocks = scala.io.Source.fromInputStream(is).mkString
    val result = initialStocks.split(":").toList
    val fingerSize = result(1).toInt
    fingerSize
  }

  //calculates the chordsize
  def getChordSize(fingerSize : Int): Int = {
    val chordSize = Math.pow(2, fingerSize).toInt
    chordSize
  }

  //initilaizes the finger table
  def initFingerTable(Node: Int , nodeName : Int, m :Int): collection.mutable.Map[Int, Int] = {
    var table = collection.mutable.Map[Int, Int]()
    if (Node == -1) {
      for (i <- 0 until m) {
        table += (i -> nodeName)
      }
    }
    table
  }

  //builds the finger table
  def fixFinger( Node : ChordNode, index: Int, i: Int, nextIndex: Int, m :Int) : Unit = {
    Node.finger1.update(i, index)
    if ( i + 1 < m) {
      var successorActor = Node.context.actorSelection(Node.prefix + Node.successor)
      successorActor ! Messages.findSuccessor(nextIndex, Node.hashName, Node.request,  i + 1)
    } else {
      Node.self ! Messages.updateFingers()
    }
  }

  //updates the finger table
  def updateFingerTable( nodeName : Int, m :Int) : collection.mutable.Map[Int, Int] = {
    var updatedTable = collection.mutable.Map[Int, Int]()
    for (i <- 0 until m) {
      var res = -1
      val size = Math.pow(2, i).toInt;
      if (size < nodeName) {
        res = (nodeName - size) % (Math.pow(2, m).toInt)
      } else {
        res = (Math.pow(2, m).toInt) - Math.abs(nodeName - size)
      }
      updatedTable += (i -> res)
    }
    updatedTable
  }

  //prints the finger table
  def printFingerTable(Node : ChordNode) : Unit = {
    println("**** Finger Table of " + Node.hashName + " *****")
    for(x <- 0 until Node.finger1.size){
      println("Finger " + x + " --> " + Node.finger1.get(x).get)
    }
    println("------------------------------------------------------------")
  }
}
