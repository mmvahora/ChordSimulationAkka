import spray.json._
import spray.json.DefaultJsonProtocol._
import scala.collection.immutable.Map

object JSONResponse {
    def Success : String = {
      Success(Map())
    }

    def Success(data : Map[String, String]) : String = {
      (Map("OK" -> JsBoolean(true)) ++ data.mapValues(JsString(_))).toJson.toString()
    }

    def Failure(error : String) : String = {
      Failure(List(error))
    }

    def Failure(errors : List[String]) : String = {
      Map(
        "OK" -> JsBoolean(false),
        "Errors" -> errors.toJson
      ).toJson.toString()
    }
}