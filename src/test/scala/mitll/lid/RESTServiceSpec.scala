/*
 * Copyright 2013-2016 Massachusetts Institute of Technology, Lincoln Laboratory
 * version 0.2
 * author: Wade Shen, Jennifer Williams, Gordon Vidaver
 * dagli@ll.mit.edu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mitll.lid


import java.net.URLEncoder

import com.typesafe.scalalogging.LazyLogging
import org.scalatest._

import scala.io.Source

class RESTServiceSpec extends FlatSpec with Matchers with LazyLogging {
  it should "start a service" in {
    val service = new RESTService
    val label = classify("This")
    service.stopServer
  }

  it should "test 4 against REST service" in {
    val service = new RESTService()

    val lidNativeDocs = "test/news4L-500each.tsv"
    val overallAccuracy = new LID().testREST(lidNativeDocs)
    val expected = 0.9365f
    overallAccuracy shouldBe expected +- 0.001f

    service.stopServer
  }

  def classify(input: String): String = {
    val enc = URLEncoder.encode(input)
    val url = "http://localhost:8080/classify?q=" + enc + ""
    val result = Source.fromURL(url)
    result.mkString
  }

  def classifyJSON(input: String): Array[(Double, Symbol)] = {
    val enc = URLEncoder.encode(input)
    val url = "http://localhost:8080/classifyJSON?q=" + enc + ""

    val html = Source.fromURL(url)
    val s = html.mkString
    val split = s.split("class\": \"")
    val split2 = s.split("confidence\": ")

    val tail = split(1)
    var resp = tail.split("\"").head
    if (resp.endsWith(".txt")) resp = resp.dropRight(4)
    val conf = split2(1).split("\"").head.split(",").head
    val dv = conf.toDouble
    Array(dv -> Symbol(resp))
  }

}
