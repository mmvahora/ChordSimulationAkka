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

  test("checkMsgDgstSpecified"){
    val test = conf.getString("MSG_DGST")
    assert(test.length > 0)
  }

  test("CheckCSVLoads")
  {
    val fileStream = getClass.getResourceAsStream("/movies.csv")
    val lines = Source.fromInputStream(fileStream).getLines
    assert(lines.next()!=null)
  }

  test("CheckCSVLines")
  {
    val fileStream = getClass.getResourceAsStream("/movies.csv")
    val lines = Source.fromInputStream(fileStream).getLines
    var count=0
    lines.foreach(line=>count=count+1)
    assert(count==15454)
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

    test("CheckLesserConditionTrue")
  {
    val check=Utilities.checkLesserCondition(-100, 0, false )
    assert(check)
  }

  test("CheckLesserConditionFalse")
  {
    val check=Utilities.checkLesserCondition(-100, 0, true )
    assert(!check)
  }

  test("TestHashString")
  {
    var check =0
    check=Utilities.mkHash("title", Utilities.getChordSize(10))
    assert(check>0)
  }

  test("TestHashEmpty")
  {
    var check =0
    check=Utilities.mkHash("", Utilities.getChordSize(10))
    assert(check==0)
  }


}