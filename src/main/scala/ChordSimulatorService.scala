import java.io.{EOFException, RandomAccessFile}
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
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

import scala.collection.JavaConverters._
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
  private val stats = new ConcurrentHashMap[String, AtomicLong]()
  final val READ: Byte = 0
  final val WRITE: Byte = 0
  private val hopCounts = new ConcurrentHashMap[Int, AtomicInteger]()

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
                val firstNodeHash =  Utilities.mkHash("1", Utilities.getChordSize(job.fingerSize))
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
                  val userExecutionPlan: Array[ListBuffer[Byte]] = Array.fill[ListBuffer[Byte]](numTicks+1)(ListBuffer[Byte]())

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
                    val userExecutionPlan = executionPlan.get(user.toString())
                    val userPlan = userExecutionPlan.get(timeCounter)

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
                  var computerCollectCount = 0

                  if (job.timeMarks.contains(timeCounter)) {
                    logging.info("Taking snapshot...")
                    isPaused = true
                    collectCount = users.length

                    for ((user, i) <- users.zipWithIndex) {
                      ask(user, collect()).mapTo[ConcurrentHashMap[String, AtomicLong]].onComplete {
                        case Success(userStats) =>
                          logging.info("Collect from user " + i)

                          // add stats
                          for ((k, v) <- userStats.asScala.toMap) {
                            stats.putIfAbsent(k, new AtomicLong(0L))
                            stats.get(k).addAndGet(v.get)
                          }

                          collectCount -= 1

                        case Failure(e) =>
                          hasError = true
                          logging.error("Unable to collect from user - " + e.getMessage)
                          collectCount -= 1

                        case _ => logging.error("Invalid collect status")
                      }
                    }

                    var computerCollectCount = computers.length
                    for ((computer, i) <- computers.zipWithIndex) {
                      ask(computer, nodeCollect()).mapTo[ConcurrentHashMap[Int, AtomicInteger]].onComplete {
                        case Success(computerStats) =>
                          logging.info("Collect from computer " + i)

                          // add stats
                          for ((k, v) <- computerStats.asScala.toMap) {
                            hopCounts.putIfAbsent(k, new AtomicInteger(0))
                            hopCounts.get(k).addAndGet(v.get)
                          }

                          computerCollectCount -= 1

                        case Failure(e) =>
                          hasError = true
                          logging.error("Unable to collect from computer - " + e.getMessage)
                          computerCollectCount -= 1

                        case _ => logging.error("Invalid collect status")
                      }
                    }
                  }

                  var waitTimeout = 0
                  do {
                    Thread.sleep(timeInterval * 1000)
                    waitTimeout += timeInterval
                  } while (collectCount > 0 && waitTimeout <= 2)

                  if (waitTimeout > 2) {
                    // timeout with collect... so stop simulation
                    logging.error("Simulation stopped due to collect timeout")
                    chordSystem.terminate()
                    hasError = true
                    break
                  }
                }

                // send PoisonPill to all (will be processed after all other messages sent...)
                users.foreach( _ ! PoisonPill )
                computers.foreach( _ ! PoisonPill )

                Await.ready(chordSystem.whenTerminated, Duration(job.simulationDuration + 10, TimeUnit.SECONDS))
                //chordSystem.terminate()

                dataRAF.close()

                if (hasError) {
                  complete(JSONResponse.Success(Map("Status" -> "Error")))
                } else {
                  complete(JSONResponse.Success(Map("Status" -> "OK", "stats" -> statsToJson(stats), "hopCounts" -> hopToJson(hopCounts))))
                }
              }
          }
        }
    })

    route
  }

  def statsToJson(i : ConcurrentHashMap[String, AtomicLong]) : String = {
    i.asScala.map( f => (f._1, f._2.get)).toMap.toJson.toString
  }

  def hopToJson(i : ConcurrentHashMap[Int, AtomicInteger]) : String = {
    i.asScala.map( f => (f._1, f._2.get)).toMap.toJson.toString
  }

//  hopCounts.toJson.toString

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
    buildComputerActor(computerNodeID, job, actor, -1)
  }

  private def buildComputerActor(computerNodeID: Int, job: Job, actor: ActorSystem, firstNodeHash: Int): Unit = {
    val hashName = Utilities.mkHash(computerNodeID.toString, Utilities.getChordSize(job.fingerSize)).toString
    val props = Props(classOf[ChordNode], computerNodeID)
    val computerActor = actor.actorOf(props, hashName)
    computers += computerActor
    computerActor ! joinNode(firstNodeHash)

    // allow first node to come up before rest
    if (firstNodeHash == -1) Thread.sleep(1000) else Thread.sleep(200)
  }

  private def buildUserActor(userNodeID: Int, job: Job, actor: ActorSystem): Unit = {
    val userName = "u-" + userNodeID
    val props = Props(classOf[actorUser], userName, job.fingerSize)
    val userActor = actor.actorOf(props, userName)
    users += userActor
  }
}
