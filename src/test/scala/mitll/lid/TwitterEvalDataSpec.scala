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

import java.io.{File, FileWriter}

import com.typesafe.scalalogging.LazyLogging
import mitll.lid.utilities.FileLines
import org.scalatest._

class TwitterEvalDataSpec extends FlatSpec with Matchers with LazyLogging {
   // check labels

  ignore should "return labels of recall model" in {
    val newsRunner = new mitll.lid.Scorer("models/tweetRecall.mod")
    val labels = newsRunner.getLabels
    labels should contain only("am", "ar", "bg", "bn", "bo", "bs", "ca", "ckb", "cs", "cy", "da", "de", "dv", "el",
      "en", "es", "et", "eu", "fa", "fi", "fr", "gu", "he", "hi", "hi-Latn", "hr", "ht", "hu", "hy", "id", "is", "it",
      "ja", "ka", "km", "kn", "ko", "lo", "lv", "ml", "mr", "my", "ne", "nl", "no", "pa", "pl", "ps", "pt", "ro", "ru",
      "sd", "si", "sk", "sl", "sr", "sv", "ta", "te", "th", "tl", "tr", "uk", "ur", "vi", "zh-CN", "zh-TW")
  }

  ignore should "return labels of zipped recall model" in {
    val newsRunner = new mitll.lid.Scorer("models/tweetRecall.mod.gz")
    val labels = newsRunner.getLabels
    labels should contain only("am", "ar", "bg", "bn", "bo", "bs", "ca", "ckb", "cs", "cy", "da", "de", "dv", "el",
      "en", "es", "et", "eu", "fa", "fi", "fr", "gu", "he", "hi", "hi-Latn", "hr", "ht", "hu", "hy", "id", "is", "it",
      "ja", "ka", "km", "kn", "ko", "lo", "lv", "ml", "mr", "my", "ne", "nl", "no", "pa", "pl", "ps", "pt", "ro", "ru",
      "sd", "si", "sk", "sl", "sr", "sv", "ta", "te", "th", "tl", "tr", "uk", "ur", "vi", "zh-CN", "zh-TW")
  }

  ignore should "return labels of precision model" in {
    val newsRunner = new mitll.lid.Scorer("models/tweetPrecision.mod")
    val labels = newsRunner.getLabels
    labels should contain only("am", "ar", "bn", "ckb", "de", "el", "en", "es", "fa", "fr", "gu", "he", "hi",
      "hi-Latn", "hy", "id", "it", "ja", "ka", "km", "kn", "lo", "ml", "mr", "my", "ne", "nl", "pa", "pl", "ps",
      "pt", "ru", "sd", "si", "sr", "sv", "ta", "te", "th", "und", "ur", "vi", "zh-CN", "zh-TW")
  }

  ignore should "return labels of uniform model" in {
    val newsRunner = new mitll.lid.Scorer("models/tweetUniform.mod")
    val labels = newsRunner.getLabels
    labels should contain only("ar", "en", "es", "fr", "id", "ja", "ko", "pt", "ru", "th", "tl", "tr", "und")
  }

  // use test set another model
  ignore should "use recall model to test uniform" in {
    val args = "-test test/uniformCorpus.tsv -model models/tweetRecall.mod"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.7981448f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "merge precision eval json" in {
    val originJSON = "test/precision.json"
    val labelFile = "test/precision_oriented.tsv"
    val outputCorpus = "test/precisionCorpus.tsv"

    makeOurFormat(originJSON, labelFile, outputCorpus)

    true shouldBe true
  }

  ignore should "merge eval recall json" in {
    val originJSON = "test/hydratedRecall.json"
    val labelFile = "test/recall_oriented.tsv"
    val outputCorpus = "test/recallCorpus.tsv"

    makeOurFormat(originJSON, labelFile, outputCorpus)

    true shouldBe true
  }

  ignore should "merge eval json" in {
    val originJSON = "test/hydratedUniform.json"
    val labelFile = "test/uniformly_sampled.tsv"
    val outputCorpus = "test/uniformCorpus.tsv"

    makeOurFormat(originJSON, labelFile, outputCorpus)

    true shouldBe true
  }

  ignore should "filter out und" in {
    filterOutLabel("test/precCorpusNorm.tsv","test/precCorpusNormNoUnd.tsv","und")
  }

  ignore should "filter out und on prec corpus" in {
    filterOutLabel("test/precisionCorpus.tsv","test/precisionCorpusNoUnd.tsv","und")
  }

  it should "filter out und on uniform corpus" in {
    filterOutLabel("test/uniformCorpus.tsv","test/uniformCorpusNoUnd.tsv","und")
  }

  it should "filter out und on uniform norm corpus" in {
    filterOutLabel("test/uniformCorpusNorm.tsv","test/uniformCorpusNormNoUnd.tsv","und")
  }


  def filterOutLabel(file: String, outfile: String, labelOut: String): Unit = {
    val LabelText =
      """(?s)^(\S+)\t+(.*)$""".r

    val LabelTextSpace =
      """^\s*(\S+)\s+(.*)$""".r


    val filtered = FileLines(file).filter(line => {
      line match {
        case LabelText(label, text) => !label.equals(labelOut)
        case LabelTextSpace(label, text) => !label.equals(labelOut)
        case _ => true
      }
    })

    new File(outfile).delete()
    val writer = new FileWriter(outfile, true)
    filtered.foreach(line => {
      writer.write(line)
      writer.write("\n")
    })
    writer.close()
  }

  ignore should "clean quotes" in {
    clean()
  }

  def clean(): Unit = {
    val dir = new File("test")
    val cleaned = new File("cleaned")
    cleaned.mkdir();
    dir.listFiles().foreach {
      file =>
        if (file.getName.contains("orpus")) {
          println("cleaning " +file.getName)
          cleanQuotes(file.getAbsolutePath,"cleaned/"+file.getName)
        }
    }
  }

  def cleanQuotes(quotes: String, outputCorpus: String): Unit = {
    new File(outputCorpus).delete()
    val writer = new FileWriter(outputCorpus, true)

    FileLines(quotes).foreach {
      line =>
        if (line.endsWith("\"")) {
          var outline = line.dropRight(1)
          writer.write(outline)
          writer.write("\n")
        }
        else {
          writer.write(line)
          writer.write("\n")
        }
    }

    writer.close()
  }

  // TODO : filter out small classes, e.g. or
  def makeOurFormat(originJSON: String, labelFile: String, outputCorpus: String): Unit = {
    var idToContent = Map[String, String]()
    val exampleTwitterID: String = "489241303667204098"

    FileLines(originJSON).foreach {
      line =>
        val parts = line.split("\",\"")
        val id = line.substring(2, 2 + exampleTwitterID.length)
        val content = line.substring(5 + exampleTwitterID.length).dropRight(1)
        if (id.trim.isEmpty) {
          logger.error("missing id for " + line)
        }
        //  else logger.debug("id '" +id+ "'")
        idToContent += (id -> content.trim)
    }

    var idToLabel = Map[String, String]()

    FileLines(labelFile).foreach {
      line =>
        val split = line.split("\\s++")
        val id = split(1)
        val label = split(0)
        if (label.nonEmpty) {
          idToLabel += (id -> label)
        }
    }

    println("labels " + idToLabel.size + " id->content " + idToContent.size)
    var unique = Set[String]()
    unique ++= idToLabel.values
    println("labels (" + unique.size + ") : " + unique.mkString(","))

    new File(outputCorpus).delete()
    val writer = new FileWriter(outputCorpus, true)

    var c = 0
    idToContent foreach {
      case (id, content) =>
        if (idToLabel.isDefinedAt(id)) {
          val label = idToLabel(id)
          writer.write(label)
          writer.write("\t")
          writer.write(content)
          writer.write("\n")
          c += 1
        }
    }
    writer.close()
    println("wrote " + c + " lines")
  }

}
