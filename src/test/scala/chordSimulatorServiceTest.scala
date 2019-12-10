import java.nio.file.Paths

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import spray.json._
import org.scalatest.concurrent.ScalaFutures
import scala.io.Source


//import scala.concurrent.ExecutionContext.Implicits.global
//
//trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
//  implicit val jobFormat: RootJsonFormat[Job] = jsonFormat9(Job)
//}

class chordSimulatorServiceTest
  extends FlatSpec
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures
  with ScalatestRouteTest
  with JsonSupport {

  val conf: Config = ConfigFactory.load("chordConfig")
  private val route = ChordSimulatorService.getRoutes

//  override def afterAll: Unit = {
//    TestKit.shutdownActorSystem(system)
//  }

  //submitFile
  "Submit File" should "Error if file is not a csv" in {
    val f = Multipart.FormData(Multipart.FormData.BodyPart.Strict(
      "submitFile",
      HttpEntity(ContentTypes.`text/plain(UTF-8)`, "This is a test"),
      Map("filename" -> "testFile.txt")
    ))

    var posted = false

    Post("/submitFile", f) ~> route ~> check {
      posted = true
      status shouldEqual StatusCodes.OK

      val jsobj = responseAs[String].parseJson.asJsObject()
      jsobj.fields("OK").convertTo[Boolean] shouldBe false
      jsobj.fields("Errors").convertTo[List[String]] should contain ("File must be CSV")
    }

    posted shouldBe true
  }

  it should "Store csv file and return file id" in {
    val f = Multipart.FormData(Multipart.FormData.BodyPart.Strict(
      "submitFile",
      HttpEntity(ContentTypes.`text/csv(UTF-8)`, "\"MovieOne\",\"One\"\n\"MovieTwo\",\"Two\""),
      Map("filename" -> "testFile.csv")
    ))

    var posted = false

    Post("/submitFile", f) ~> route ~> check {
      posted = true

      status shouldBe StatusCodes.OK

      val jsobj = responseAs[String].parseJson.asJsObject()
      jsobj.fields("OK").convertTo[Boolean] shouldBe true
      jsobj.fields("fileID").convertTo[String] should not be empty

      // verify file
      val s = Source.fromFile(Paths.get(conf.getString("FILE_SAVE_PATH"), jsobj.fields("fileID").convertTo[String]).toFile)
      val contacts = s.mkString
      s.close()

      contacts shouldBe "\"MovieOne\",\"One\"\n\"MovieTwo\",\"Two\""
    }

    posted shouldBe true
  }

  //submitJob
  "Submit job" should "Process movies.csv" in {
    val job = Job(2, 2, 5, 10, 20, 15, List(5, 10), "movies.csv", 0.5F)
    val jobEntity = Marshal(job).to[MessageEntity].futureValue
    var posted = false

    Post("/submitJob").withEntity(jobEntity) ~> route ~> check {
      posted = true

      status shouldBe StatusCodes.OK

      var jsobj = responseAs[String].parseJson.asJsObject()
      System.out.println(jsobj.toString) // @todo remove debug

      jsobj.fields("OK").convertTo[Boolean] shouldBe true



//      val jsobj = responseAs[String].parseJson.asJsObject()
//      jsobj.fields("OK").convertTo[Boolean] shouldBe true
//      jsobj.fields("fileID").convertTo[String] should not be empty
//
//      // verify file
//      val s = Source.fromFile(Paths.get(conf.getString("FILE_SAVE_PATH"), jsobj.fields("fileID").convertTo[String]).toFile)
//      val contacts = s.mkString
//      s.close()
//
//      contacts shouldBe "\"MovieOne\",\"One\"\n\"MovieTwo\",\"Two\""
    }

    posted shouldBe true
  }
}
