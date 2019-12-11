
import akka.actor.ActorSelection
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}
import java.security.MessageDigest
import java.lang.Long

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.typesafe.config.ConfigFactory

import scala.collection.concurrent.TrieMap

object Utilities {

  val logger = LoggerFactory.getLogger(this.getClass)
  val conf = ConfigFactory.load("chordConfig")
  val MsgDgstAlgo = conf.getString("MSG_DGST")
  val hashBase=conf.getInt("hashBase")
  val myLogger: Logger = LoggerFactory.getLogger(this.getClass)

  def mkHash(title: String, hashSize: Int): Int = {
    myLogger.info("Before Hashing: " + title)
    if (title.length() > 0)
    {
      val hashed = MessageDigest.getInstance(MsgDgstAlgo).digest(title.getBytes)
      var hashedVal=hashed.map("%x" format _).mkString.trim()
      if (hashedVal.length() > hashBase-1)
      {
        hashedVal = hashedVal.slice(0, hashBase-1);
      }
      //take the hashed value, convert to long using hashBase
      //take our base 10 hash and % by the chordring hashSize to make sure it fits
      //return converted int
      val hash = Long.parseLong(hashedVal, hashBase)
      val hashDec = (hash % hashSize)
      myLogger.info("After Hashing: " + hashDec.toInt)
      hashDec.toInt
    }
    else
    {
      0
    }
  }

  def buffer() : Unit = {
    var bufferPeriod: Int = (0.005 * Simulator.numberOfNodes * Simulator.numberOfRequests).toInt
    while ((Simulator.count < (Simulator.numberOfNodes * Simulator.numberOfRequests) - bufferPeriod)) {
      Thread.sleep(1000)
    }
  }
  /**
   * get finger size from the input folder
   */
  def getFingerSize(filename : String): Int = {
    val is = Thread.currentThread.getContextClassLoader.getResourceAsStream(filename)
    val initialStocks = scala.io.Source.fromInputStream(is).mkString
    val result = initialStocks.split(":").toList
    val fingerSize = result(1).toInt
    logger.info("Finger Size" + fingerSize)
    fingerSize
  }
  /**
   * Calculates the chordsize with the given finger size
   */
  def getChordSize(fingerSize : Int): Int = {
    val chordSize = Math.pow(2, fingerSize).toInt
    logger.info("Chord Size" + chordSize)
    chordSize
  }
  /**
   * Initialize finger table
   */
  def initFingerTable(Node: Int , nodeName : Int, m :Int): collection.mutable.Map[Int, Int] = {
    var table = collection.mutable.Map[Int, Int]()
    if (Node == -1) {
      for (i <- 0 until m) {
        table += (i -> nodeName)
      }
    }
    logger.info("Initial Finger Table" + table)
    table
  }
  /**
   * building finger table of the incoming node
   */
  def buildFinger(Node : ChordNode, index: Int, i: Int, nextIndex: Int, m :Int) : Unit = {
    logger.info("The chord node is" +Node )
    Node.fingerTable(i) = index
    if ( i + 1 < m) {
      //successor of the incoming nodes
      val successor = Node.context.actorSelection(Simulator.pathPrefix + Node.successor)
      successor ! search_successor(nextIndex, Node.nodeID, i + 1)
    } else {
      Node.self ! updateFingers()
    }
  }
  /**
   * Calculates and updates the entry of the fingers
   */
  def updateFingerTable( nodeName : Int, m :Int) : collection.mutable.Map[Int, Int] = {
    var updatedTable = collection.mutable.Map[Int, Int]()
    for (i <- 0 until m) {
      var res = -1
      val size = Math.pow(2, i).toInt;
      if (size < nodeName) {
        res = (nodeName - size) % (Math.pow(2, m).toInt)
        logger.debug("The resulting value" + res)
      } else {
        res = (Math.pow(2, m).toInt) - Math.abs(nodeName - size)
        logger.debug("The resulting value" + res)
      }
      updatedTable += (i -> res)
    }
    logger.debug("Updated Table : " + updatedTable)
    updatedTable
  }
  /**
   * Cases to check if the current node is the successor or predecessor of the passed key.
   * These cases are used in searchSuccessor and searchPredecessor implementations.
   */
  def getCases(nodeID: Int, successor: Int, key: Int): Tuple3[Boolean,Boolean,Boolean] = {
    logger.info("Check cases for:"+ nodeID + " and "+ successor)
    val nodeEqualsSuccessor = nodeID == successor
    logger.debug("Node Equal To Successor: "+ nodeEqualsSuccessor)
    val successorIsLarger = List(successor > nodeID,List(key >= nodeID , key < successor).reduce(_&&_)).reduce(_&&_)
    logger.debug("Successor Is Larger: "+ successorIsLarger)
    val nodeIsGreater = List(successor < nodeID , List(key >= nodeID && key < Simulator.chordSize, key >= 0 && key < successor).reduce(_||_)).reduce(_&&_)
    logger.debug("Node Is Larger: "+ nodeIsGreater)
    (nodeEqualsSuccessor, successorIsLarger, nodeIsGreater)
  }
  /**
   * findPredSetRequest: Sets and updates the predecessor if found when the "SetRequest" variable is sent.
   *
   */
  def findPredSetRequest(Node : ChordNode, cases: Tuple3[Boolean,Boolean,Boolean], act: ActorSelection, f: search_predecessor, nearestNbr: Int): Unit = {

    logger.info("Predecessor Set Request")
    if (List(cases._1, cases._2, cases._3).reduce(_||_)) {
      logger.info("The current Predecessor is the Predecessor of the given key")
      tempActPre(act, f, Node.nodeID, false, false)
    } else {
      logger.info("Pass the key to the closest preceeding node")
      if (Node.fingerTable(nearestNbr) == Node.nodeID) {
        tempActPre(act, f, Node.nodeID, false, false)
      } else {
        val act = Node.context.actorSelection(Simulator.pathPrefix + Node.fingerTable(nearestNbr))
        tempActPre(act,f, Node.nodeID,false,true)
      }
    }
  }
  /**
   * Function to implement the temporary actor to fix the finger with the given successor or search for the successor if not found.
   */
  def tempActSucc(transActor: ActorSelection, key: Int, name: Int, i: Int, param: Boolean): Unit ={
    logger.info("Successor: "+ name + " parameter: "+ param)
    if(!param){
      transActor ! fixFinger(name, i)
    }else{
      transActor ! search_successor(key, name, i)
    }
  }
  /**
   * Function to implement the temporary actor to fix the finger with the given predecessor or search for the predecessor if not found.
   */
  def tempActPre(transActor: ActorSelection, f: search_predecessor, nodeName: Int, bool: Boolean, param: Boolean): Unit ={
    logger.info("Node: "+ nodeName + " parameter: "+ param)
    if(!param) {
      transActor ! set_predecessor(nodeName, bool)
    } else if(param) {
      transActor ! f
    }
  }
  /**
   * Cases to verify if the current node is the successor or predecessor we are looking for.
   */
  def checkGreaterCondition(obj1: Int, obj2: Int, obj3: Int): Boolean = {
    val stateOne = List(obj1 < obj2 , obj2 < Simulator.chordSize).reduce(_&&_)
    logger.info("Case One: "+ stateOne)
    val stateTwo = List(0 < obj2 , obj2 < obj3).reduce(_&&_)
    logger.info("Case Two: "+ stateTwo)
    List(stateOne && !stateTwo , !stateOne && stateTwo).reduce(_||_)
  }

  /*
  Used for closestprecedingfinger conditions
  */
  def checkLesserCondition(a: Int, b: Int, c: Boolean): Boolean =
  {
    val cond1 = ((a<0) && (a<b))
    (cond1 && (c==false))
  }

  def printFingerTable(Node : ChordNode) : Unit = {
    println("** Finger Table of " + Node.nodeID + " ***")
    println("Predecessor: " + Node.predecessor + " Successor: " + Node.successor + " Hop Count " +Node.hopCount)
    Node.associatedKeyData foreach(x => println(x._1 + " --> " + x._2))
    Node.associatedKeyData foreach(x => println(x._1 + " --> " + x._2.size))
    for(x <- 0 until Node.fingerTable.size){
      println("Finger " + x + " --> " + Node.fingerTable.get(x).get)
    }
    println("------------------------------------------------------------")
  }

  def getNodeForKey(Movie_Name : String, NodeData :  TrieMap[Int, Int], MovieData : TrieMap[Int, String]) : Int = {
  //val key = Simulator.keyToMovies.find(_._2.contains(Movie_Name)).map(_._1).get
    //val key = Simulator.keyToMovies.find(_._2.contains(Movie_Name)).getOrElse(-1,"")._1
    val key = MovieData.find(_._2.contains(Movie_Name)).getOrElse(-1,"")._1
    if(key == -1)
     -1
    else{
      //val res = Simulator.keyToNode(key)
      val res = NodeData(key)
      res
    }
  }


}