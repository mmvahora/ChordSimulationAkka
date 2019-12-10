import java.io.{EOFException, RandomAccessFile}
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.TimeUnit

import actorUser.{read, write}
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import spray.json.{DefaultJsonProtocol, _}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.io.StdIn
import scala.util.control.Breaks._
import scala.util.{Failure, Random, Success}

// Request Job class (to handle json post)
final case class Job(
    numUsers: Int,
    numComputers: Int,
    fingerSize: Int,
    perActorMinReq: Int,
    perActorMaxReq: Int,
    simulationDuration: Int,
    timeMarks: List[Int],
    fileID: String,
    readWriteRatio: Float
  )

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val jobFormat: RootJsonFormat[Job] = jsonFormat9(Job)
}

object ChordSimulatorService extends Directives with JsonSupport {
  private val conf = ConfigFactory.load("chordConfig")
  implicit val system: ActorSystem = ActorSystem("ChordSimulator")
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(5 seconds) // default to 5 second timeout
  private var users = new ListBuffer[ActorRef]()
  private var computers = new ListBuffer[ActorRef]()
  private val logging = LoggerFactory.getLogger("Service")
  private var isPaused = false
  private val stats = new mutable.HashMap[String, Int]()
  final val READ: Byte = 0
  final val WRITE: Byte = 0

  def main(args: Array[String]): Unit = {
    val route = getRoutes

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

  def getRoutes: Route = {
    // routes implementation reference https://doc.akka.io/docs/akka-http/current/common/json-support.html
    val route = concat(
      path("submitFile") {
        post {
          val uuid = UUID.randomUUID.toString

          // accept file, write to storage (directory based on config), and return fileID (UUID)
          fileUpload("submitFile") {
            case (fileInfo, fileStream) =>
              // fileName, must end in csv
              if (fileInfo.fileName.toLowerCase.endsWith(".csv")) {
                val sink = FileIO.toPath(Paths.get(conf.getString("FILE_SAVE_PATH")) resolve uuid)
                val writeResult = fileStream.runWith(sink)

                onSuccess(writeResult) {
                  result =>
                    result.status match {
                      case Success(_) => complete(JSONResponse.Success(Map("fileID" -> uuid)))
                      case Failure(e) => complete(JSONResponse.Failure(e.getMessage))
                    }
                }
              } else {
                complete(JSONResponse.Failure("File must be CSV"))
              }
          }
        }
      },

      path("submitJob2") {
        post {
          complete("OK")
        }
      },

      path("submitJob") {
        post {
          entity(as[Job]) {
            job =>
              if (job.numComputers < 0) {
                complete(JSONResponse.Failure("Must have at least 1 computer (node)."))
              } else {
                // open file
                val dataRAF = new RandomAccessFile(conf.getString("FILE_SAVE_PATH") + "/" + job.fileID, "r")

                val chordSystem = ActorSystem("chord-system")

                // create nodes
                val firstNodeHash = Hashing.getHash("1", Utilities.getChordSize(job.fingerSize))
                buildComputerActor(1, job, chordSystem)

                // build nodes after first
                for (thisNodeID <- 2 to job.numComputers) {
                  logging.info("Starting computer "+thisNodeID)
                  buildComputerActor(thisNodeID, job, chordSystem, firstNodeHash)
                }

                // build users
                for (thisUserID <- 1 to job.numUsers) {
                  logging.info("Starting users "+thisUserID)
                  buildUserActor(thisUserID, job, chordSystem)
                }

                val timeInterval = 1
                var timeCounter = 0
                var hasError = false

                // user -> time -> List of actions
                val executionPlan = new mutable.HashMap[String, Array[ListBuffer[Byte]]]()
                val randRange = job.perActorMaxReq - job.perActorMinReq + 1

                // build execution plan for each user
                for (user <- users) {
                  val numRequests = Random.nextInt(randRange) + job.perActorMinReq
                  val numReadRequests = (numRequests * job.readWriteRatio).floor.toInt
                  val numWriteRequests = numRequests - numReadRequests
                  var numUsedRead = 0
                  var numUsedWrite = 0

                  // build up execution plan randomly
                  val numTicks = (job.simulationDuration / timeInterval).floor.toInt
                  val userExecutionPlan: Array[ListBuffer[Byte]] = Array.fill[ListBuffer[Byte]](numTicks)(ListBuffer[Byte]())

                  // build reads and write execution path for this user
                  var needRead = true
                  var needWrite = true

                  while (needRead || needWrite) {
                    val randTime = Random.nextInt(job.simulationDuration + 1)

                    if (needRead && !needWrite) {
                      // add read
                      userExecutionPlan(randTime).append(READ)
                      numUsedRead += 1
                    } else if (needWrite && !needRead) {
                      // add write
                      userExecutionPlan(randTime).append(WRITE)
                      numUsedWrite += 1
                    } else if (Random.nextFloat <= 0.5) {
                      // add read
                      userExecutionPlan(randTime).append(READ)
                      numUsedRead += 1
                    } else {
                      // add write
                      userExecutionPlan(randTime).append(WRITE)
                      numUsedWrite += 1
                    }

                    if (numUsedRead >= numReadRequests) {
                      needRead = false
                    }

                    if (numUsedWrite >= numWriteRequests) {
                      needWrite = false
                    }
                  }

                  // add to overall execution plan
                  executionPlan(user.toString) = userExecutionPlan
                }

                logging.debug(executionPlan.mkString)

                // event loop
                while (timeCounter <= job.simulationDuration) {
                  // action -- fire events and log
                  for (user <- users) {
                    val userPlan = executionPlan.get(user.toString()).asInstanceOf[Array[List[Byte]]](timeCounter)

                    // "ask" each user to read or write
                    for (task <- userPlan) {
                      task match {
                        case READ => user ! read(getFromFile(dataRAF))
                        case WRITE => user ! write(getFromFile(dataRAF))
                      }
                    }
                  }

                  // increment time counter
                  timeCounter += timeInterval

                  // collect results (if indicated by timeMarks)
                  var collectCount = 0

                  if (job.timeMarks.contains(timeCounter)) {
                    logging.info("Taking snapshot...")
                    isPaused = true
                    collectCount = users.length

                    for ((user, i) <- users.zipWithIndex) {
                      ask(user, actorUser.collect()).mapTo[mutable.HashMap[String, Int]].onComplete {
                        case Success(userStats) =>
                          logging.info("Collect from user " + i)

                          // add stats
                          for ((k, v) <- userStats) {
                            stats(k) += v
                          }

                          collectCount -= 1

                        case Failure(e) =>
                          hasError = true
                          logging.error("Unable to collect from user - " + e.getMessage)
                          collectCount -= 1
                      }
                    }
                  }

                  var waitTimeout = 0
                  do {
                    Thread.sleep(timeInterval * 1000)
                    waitTimeout += timeInterval
                  } while (collectCount > 0 || waitTimeout > 5)

                  if (waitTimeout > 5) {
                    // timeout with collect... so stop simulation
                    logging.error("Simulation stopped due to collect timeout")
                    hasError = true
                    break
                  }
                }

                Await.ready(chordSystem.whenTerminated, Duration(job.simulationDuration + 0.25, TimeUnit.MINUTES))

                dataRAF.close()

                if (hasError) {
                  complete(JSONResponse.Success(Map("Status" -> "Error")))
                } else {
                  complete(JSONResponse.Success(Map("Status" -> "OK")))
                }
              }
          }
        }
    })

    route
  }

  private def getFromFile(dataRAF : RandomAccessFile): String = {
    try {
      ExtractFirstField(dataRAF.readLine())
    } catch {
      case e : EOFException =>
        dataRAF.seek(0)
        ExtractFirstField(dataRAF.readLine())
    }
  }

  private def ExtractFirstField(s : String) : String = {
    s.split(",")(0).trim.stripPrefix("\"").stripSuffix("\"").trim
  }


  private def buildComputerActor(computerNodeID: Int, job: Job, actor: ActorSystem): Unit = {
    buildComputerActor(computerNodeID.toString, job, actor, -1)
  }

  private def buildComputerActor(computerNodeID: Int, job: Job, actor: ActorSystem, firstNodeHash: Int): Unit = {
    buildComputerActor(computerNodeID.toString, job, actor, firstNodeHash)
  }

  private def buildComputerActor(computerNodeID: String, job: Job, actor: ActorSystem, firstNodeHash: Int): Unit = {
    val hashName = Hashing.getHash("c-" + computerNodeID, Utilities.getChordSize(job.fingerSize)).toString
    val props = Props(classOf[ChordNode], hashName, "c-" + computerNodeID, 10) // 10 hmmm... where is this used @todo
    val computerActor = actor.actorOf(props, hashName)
    computers += computerActor
    computerActor ! Messages.join(firstNodeHash)
  }

  private def buildUserActor(userNodeID: Int, job: Job, actor: ActorSystem): Unit = {
    val hashName = Hashing.getHash("u-" + userNodeID, Utilities.getChordSize(job.fingerSize)).toString
    val props = Props(classOf[actorUser], hashName)
    val userActor = actor.actorOf(props, hashName)
    users += userActor
  }
}
