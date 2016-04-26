package mitll.lid

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.http4s.MediaType._
import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.`Content-Type`
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import scalaz.concurrent.Task
import scalaz.stream._

/**
  * Simple REST Service for LLClass
  * Created by go22670 on 4/25/16.
  */
class RESTService(modelFile: String = "models/news4L.mod", port: Int = 8080) extends LazyLogging {
  val scorer = new mitll.lid.Scorer(modelFile)
  //logger.info("known classes : " + scorer.lidModel.knownClasses.map(_.name).mkString(","))

  object T extends QueryParamDecoderMatcher[String]("q")

  def rootService(implicit executionContext: ExecutionContext) = HttpService {
    case GET -> Root / "classify" :? T(search) =>
      val (language, confidence) = scorer.textLID(search)
      Ok(s"$language")

    case GET -> Root / "classifyJSON" :? T(search) =>
      textLID(search)

//    case req @ POST -> Root / "classifyJSON"  =>
//
//
//      val value = new ArrayBuffer[String]()
//      req.body to io.fillBuffer(value).run
//      textLID("dude")

    case GET -> Root / "classifyJSONAll" / query =>
      val full = scorer.textLIDFull(query)
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
    val builder = BlazeBuilder.mountService(rootService(ExecutionContext.global))
    val server: Server = builder.run
    myServer = server
    server
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
    new RESTService(model, 8080)

    Thread.sleep(6000000)
  }
}