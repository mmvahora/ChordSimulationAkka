
object Messages {

  sealed trait ChordMessage
  case class join(randNode: Int) extends ChordMessage
  case class getSuccessor(isNewNode: Boolean) extends ChordMessage
  case class setPredecessor(name: Int, isNewNode: Boolean) extends ChordMessage
  case class setSuccessor(name: Int, isNewNode: Boolean) extends ChordMessage
  case class findPredecessor(key: Int, origin: Int, reqType: String, data: String) extends ChordMessage
  case class findSuccessor(key: Int, origin: Int, reqType: String, i: Int) extends ChordMessage
  case class fixfinger(node: Int, i: Int) extends ChordMessage
  case class updateFingers() extends ChordMessage
  case class printTable() extends ChordMessage
  case class read(key : String) extends ChordMessage
  case class write(key : String) extends ChordMessage
}