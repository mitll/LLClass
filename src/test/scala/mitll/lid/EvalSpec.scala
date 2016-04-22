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

class EvalSpec extends FlatSpec with Matchers with LazyLogging {
  ignore should "train a model over movie sentiment corpus" in {
    val args = "-all test/rt-polaritydata.tsv -split 0.15 -iterations 10"
    val overallAccuracy = new LID().ep(args.split(" "))
    //    val expected = 0.870f
    //   overallAccuracy shouldBe expected +- 0.001f
    true shouldBe true
  }

  ignore should "train a model over michigan sentiment corpus" in {
    val args = "-all test/michiganTraining.txt -split 0.15  -iterations 10"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.958f
    overallAccuracy shouldBe expected +- 0.001f
  }

  it should "train a model over recall corpus" in {
    val args = "-all test/recallCorpus.tsv -split 0.15 -iterations 10 -stratify false -dropSmallerThan 500"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.923f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "train a model over recall corpus and save it" in {
    val args = "-all test/sortedRecallCorpus.tsv -split 0.15 -iterations 10 -model recall.mod"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.870f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "train a model over precision corpus and save it" in {
    val args = "-all test/precisionCorpus.tsv -split 0.15 -iterations 10 -model precision.mod"
    val overallAccuracy = new LID().ep(args.split(" "))
    true shouldBe true
    //    val expected = 0.870f
    //    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "score against a saved model" in {
    val args = "-test test/sortedRecallCorpus.tsv -model models/recall.mod"
    val accuracy = new LID().ep(args.split(" "))
    accuracy shouldBe 0.921f +- 0.001f
  }

  ignore should "score precision against a saved model" in {
    val args = "-test test/precisionCorpus.tsv -model models/recall.mod"
    val accuracy = new LID().ep(args.split(" "))
    accuracy shouldBe 0.921f +- 0.001f
  }

  ignore should "merge precision eval json" in {
    val originJSON = "test/precision.json"
    val labelFile = "test/precision_oriented.tsv"
    val outputCorpus = "test/precisionCorpus.tsv"

    makeOurFormat(originJSON, labelFile, outputCorpus)

    true shouldBe true
  }

  ignore should "merge eval json" in {
    val originJSON = "test/hydratedRecall.json"
    val labelFile = "test/recall_oriented.tsv"
    val outputCorpus = "test/recallCorpus.tsv"

    makeOurFormat(originJSON, labelFile, outputCorpus)

    true shouldBe true
  }

  // TODO : filter out small classes, e.g. or
  def makeOurFormat(originJSON: String, labelFile: String, outputCorpus: String): Unit = {
    var idToContent = Map[String, String]()
    FileLines(originJSON).foreach {
      line =>
        val parts = line.split("\",\"")
        val id = line.substring(2, 2 + "489241303667204098".length)
        val content = line.substring(5 + "489241303667204098".length).dropRight(1)
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

  ignore should "test against langid.py" in {
    //  val args = "test/twitter-11-500each.tsv.gz"
    val overallAccuracy = new LID().testLangid("test/twitter-11-500each.tsv.gz")
    val expected = 0.579f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "test 4 against langid.py" in {
    //  val args = "test/twitter-11-500each.tsv.gz"
    val lidNativeDocs: String = "test/news4L-500each.tsv"
    val overallAccuracy = new LID().testLangid(lidNativeDocs)
    val expected = 0.9365f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "split" in {
    makeOneFilePerLanguage("test/news4L-500each.tsv", "dest")
  }

  // e.g for langid
  def makeOneFilePerLanguage(lidNativeDocs: String, dest: String) = {
    new File(dest).mkdir()
    val LabelTextSpace = """^\s*(\S+)\s+(.*)$""".r

    var labelToFile = Map[String, FileWriter]()
    FileLines(lidNativeDocs).foreach {
      case (LabelTextSpace(label, text)) => {
        val writer = labelToFile.getOrElse(label, new FileWriter(dest + File.separator + label + ".txt", true))
        labelToFile += (label -> writer)
        writer.write(text + "\n")
      };
    }
    labelToFile.values.foreach { f => f.close() }
  }

  ignore should "sentiment" in {
    convertToOurFormat()
  }

  def convertToOurFormat(): Unit = {
    val s: String = "rt-polaritydata"
    val source = "test/" + s
    val dir = new File(source)

    val writer = new FileWriter("test/" + s + ".tsv", true)
    var c = 0

    dir.listFiles().foreach {
      file =>
        val path: String = file.getAbsolutePath
        println("reading " + path)
        FileLines(path, "ISO-8859-1").foreach {
          val suffix = file.getName.takeRight(3)
          line => writer.write(suffix)
            writer.write("\t")
            writer.write(line)
            writer.write("\n")
            c += 1
        }
    }
    println("wrote " + c + " lines")
    writer.close()
  }
}
