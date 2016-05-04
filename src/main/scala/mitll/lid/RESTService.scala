package mitll.lid

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.http4s.MediaType._
import org.http4s._
import org.http4s.dsl.{:?, QueryParamDecoderMatcher, _}
import org.http4s.headers.`Content-Type`
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext
import scalaz.concurrent.Task

/**
  * Simple REST Service for LLClass
  *
  * Only GETs, doesn't do POST yet.
  *
  * Created by go22670 on 4/25/16.
  */
class RESTService(modelFile: String = "models/news4L.mod", hostname: String = "localhost", port: Int = 8080)(implicit ec: ExecutionContext = ExecutionContext.global) extends LazyLogging {
  val scorer = new mitll.lid.Scorer(modelFile)

  object T extends QueryParamDecoderMatcher[String]("q")

  def rootService = HttpService {
    case GET -> Root / "labels" =>
      val labels: Seq[Json] = scorer.getLabels.map(Json.fromString)
      val jsonOuter = Json.obj("labels" -> Json.fromValues(labels))
      Ok(jsonOuter.toString())
        .withContentType(Some(`Content-Type`(`application/json`)))

    case GET -> Root / "classify" :? T(search) =>
      val (language, confidence) = scorer.textLID(search)
      Ok(s"$language")

    case GET -> Root / "classify" / "json" :? T(search) =>
      textLID(search)

    //    case req @ POST -> Root / "classifyJSON"  =>
    //      val value = new ArrayBuffer[String]()
    //      req.body to io.fillBuffer(value).run
    //      textLID("dude")

    case GET -> Root / "classify" / "all" / "json" :? T(search) =>
      val full = scorer.textLIDFull(search)
      val map = full.map {
        p => Json.obj("class" -> Json.fromString(p._1.name),
          "confidence" -> Json.fromDouble(p._2).getOrElse(Json.fromString("")))
      }
      val jsonOuter = Json.obj("results" -> Json.fromValues(map))
      Ok(jsonOuter.toString())
        .withContentType(Some(`Content-Type`(`application/json`)))
  }

  def textLID(search: String): Task[Response] = {
    val (language, confidence) = scorer.textLID(search)
    val formatted = f"$confidence%1.2f"
    val json = Json.obj("class" -> Json.fromString(language),
      "confidence" -> Json.fromString(formatted))
    Ok(json.toString())
      .withContentType(Some(`Content-Type`(`application/json`)))
  }

  var myServer: Server = null

  def getServer = {
    val task = BlazeBuilder.bindHttp(port,hostname)
      .mountService(rootService, "/")
      .run

    task.awaitShutdown()

    myServer = task
  }

  def stopServer() = if (myServer != null) myServer.shutdownNow() else println("server not running")

  getServer
}

object RESTService extends LazyLogging {
  def main(args: Array[String]) {

    var model = "models/news4L.mod"
    if (args.length > 0) {
      println("Using model " + args(0))
      model = args(0)
    }
    logger.info("calling RESTService ----------- ")

    new RESTService(model, "localhost", 8080)

    Thread.sleep(Long.MaxValue)
  }
}