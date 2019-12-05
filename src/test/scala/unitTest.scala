import com.typesafe.config.ConfigFactory
import org.scalatest.FunSuite

class unitTest extends FunSuite {

  val conf = ConfigFactory.load("chordConfig")

  test("checkConfig"){
    val test = conf.getString("FILE_NAME")
    assert(test.length > 0)
  }

  test("checkFingerSize") {
    val size = Utilities.getFingerSize("input.txt")
    assert(size == 10)
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
}