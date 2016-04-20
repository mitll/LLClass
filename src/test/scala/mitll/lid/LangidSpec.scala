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

import mitll.lid.utilities.FileLines
import org.scalatest._

class LangidSpec extends FlatSpec with Matchers {
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
    val lidNativeDocs: String = "test/news4L-500each.tsv"
    val dest = "dest"
    makeOneFilePerLanguage(lidNativeDocs, dest)
  }

  def makeOneFilePerLanguage(lidNativeDocs: String, dest: String) = {
    new File(dest).mkdir()
    val LabelTextSpace =
      """^\s*(\S+)\s+(.*)$""".r

    val lines: FileLines[String] = FileLines(lidNativeDocs)
    var labelToFile = Map[String, FileWriter]()
    lines.foreach {
      case (LabelTextSpace(label, text)) => {
        val writer = labelToFile.getOrElse(label, new FileWriter(dest + File.separator + label + ".txt", true))
        labelToFile += (label -> writer)
        writer.write(text + "\n")

      };
    }
    labelToFile.values.foreach { f => f.close() }
  }
}
