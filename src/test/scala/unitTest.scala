import com.typesafe.config.ConfigFactory
import org.scalatest.FunSuite

import scala.collection.concurrent.TrieMap

class unitTest extends FunSuite {

  val conf = ConfigFactory.load("chordConfig")
  var nodeData : TrieMap[Int,Int] = TrieMap(1 -> 10, 2 -> 20)
  var movieData : TrieMap[Int,String]= TrieMap(1 -> "Titanic", 2 -> "The Dark Knight")

  test("checkConfig"){
    val test = conf.getString("FILE_NAME")
    assert(test.length > 0)
  }

  test("checkFingerSize") {
    val size = Utilities.getFingerSize("input.txt")
    assert(size == 20)
  }

  test("checkChordSize") {
    val size = Utilities.getChordSize(6)
    assert(size == 64)
  }

  test("checkFingerTableData") {
    val table = Utilities.initFingerTable(-1, 43, 3)
    assert(table.get(0).get == 43)
    assert(table.get(1).get == 43)
  }

  test("checkUpdatedTable") {
    val table = Utilities.updateFingerTable(43, 3)
    assert(table.get(0).get == 2)
  }

  test("checkGreaterCondition"){
    val greater = Utilities.checkGreaterCondition(42,54,3)
    assert(greater == true)
  }

  test("checkGreaterCondition_2"){
    val greater = Utilities.checkGreaterCondition(54,34,5)
    assert(greater == false)
  }

  test("verify getCases_1"){
    val greater = Utilities.getCases(67,34,6)
    assert(!greater._1)
    assert(!greater._2)
    assert(greater._3)
  }

  test("verify getCases_2"){
    val greater = Utilities.getCases(23,68,57)
    assert(!greater._1)
    assert(greater._2)
    assert(!greater._3)
  }

  test("checkAssociatedNodeforTheData"){
    val res = Utilities.getNodeForKey("Titanic", nodeData, movieData)
    assert(res == 10)

  }


}