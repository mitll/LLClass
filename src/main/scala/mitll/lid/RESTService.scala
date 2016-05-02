package mitll.lid

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.http4s._
import org.http4s.dsl._
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext

/**
  * Simple REST Service for LLClass
  * Created by go22670 on 4/25/16.
  */
class RESTService extends LazyLogging {
  val scorer = new mitll.lid.Scorer("models/news4L.mod")
  logger.info("known classes : " + scorer.lidModel.knownClasses.map(_.name).mkString(","))

  def rootService(implicit executionContext: ExecutionContext) = HttpService {
    case GET -> Root / "classify" / query =>
      val (language, confidence) = scorer.textLID(query)
      val formatted = f"$confidence%1.2f"
      Ok(s"$language")

    case GET -> Root / "classify" / "json" / query =>
      val (language, confidence) = scorer.textLID(query)
      val formatted = f"$confidence%1.2f"
      val json: Json = Json.obj("class" -> Json.fromString(language),
        "confidence" -> Json.fromString(formatted))
      Ok(json.toString())

    case GET -> Root / "classify" / "json" / "all" / query =>
      val full: Array[(Symbol, Double)] = scorer.textLIDFull(query)
      val map: Array[Json] = full.map {
        p => Json.obj("class" -> Json.fromString(p._1.name),
          "confidence" -> Json.fromDouble(p._2).getOrElse(Json.fromString("")))
      }
      val jsonOuter = Json.obj("results" -> Json.fromValues(map))
      Ok(jsonOuter.toString())
  }

  def getServer = {
    val builder = BlazeBuilder.mountService(rootService(ExecutionContext.global))
    val server = builder.run
    server
  }
}

object RESTService extends LazyLogging {
  def main(args: Array[String]) {
    new RESTService().getServer
    Thread.sleep(Int.MaxValue)
  }
}