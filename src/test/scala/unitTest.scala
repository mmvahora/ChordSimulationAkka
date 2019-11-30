import com.typesafe.config.ConfigFactory
import org.scalatest.FlatSpec

class unitTest extends FlatSpec {
    val conf = ConfigFactory.load("chordConfig")

    "Load config" should "load name" in 
    {
        val test = conf.getString("FILE_NAME")
        assert(test.length > 0)
    }

}