import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

// Request Job class (to handle json post)
final case class Job(
                      numUsers : Int,
                      numComputers : Int,
                      perActorMinReq : Int,
                      perActorMaxReq : Int,
                      simulationDuration : Int,
                      timeMarks : List[Int],
                      minRecordSize : Int,
                      maxRecordSize : Int,
                      avgRecordSize : Int,
                      totalRecords : Int,
                      readWriteRatio : Float
                    )

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val jobFormat : RootJsonFormat[Job] = jsonFormat11(Job)
}

object ChordSimulatorService extends Directives with JsonSupport {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("ChordSimulator")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    // needed for the future flatMap/onComplete in the end
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    // routes implementation reference https://doc.akka.io/docs/akka-http/current/common/json-support.html
    val route = {
      post {
        path("submitJob") {
          entity(as[Job]) {
            job =>
              val response =
                "numUsers = " + job.numUsers + "\n" +
                  "numComputers = " + job.numComputers + "\n" +
                  "perActorMinReq = " + job.perActorMinReq + "\n" +
                  "perActorMaxReq = " + job.perActorMaxReq + "\n" +
                  "simulationDuration = " + job.simulationDuration + "\n" +
                  "timeMarks = " + job.timeMarks.mkString(", ") + "\n" +
                  "minRecordSize = " + job.minRecordSize + "\n" +
                  "maxRecordSize = " + job.maxRecordSize + "\n" +
                  "avgRecordSize = " + job.avgRecordSize + "\n" +
                  "totalRecords = " + job.totalRecords + "\n" +
                  "readWriteRatio = " + job.readWriteRatio + "\n"

              complete(HttpResponse(entity = response))
          }
        }
      }
    }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done

  }
}
