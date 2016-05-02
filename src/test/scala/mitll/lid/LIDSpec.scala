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

import java.io.File

import org.scalatest._

// -all eval/no_nl_da_en_5k.tsv.gz -split 0.15 -iterations 10
// LID -all eval/no_nl_da_en_500.tsv.gz -split 0.15 -iterations 10
// LID -train eval/twitter-11-500each.tsv.gz -test eval/twitter-11-500each.tsv.gz
// LID -all test/news4L-500each.tsv.gz -split 0.15 -iterations 30 -model news4L.mod -log news4L.log -score news4L.score
// LID -test eval/news4L-500each.tsv -model news4L.mod
class LIDSpec extends FlatSpec with Matchers {
  // same as java -jar LLClass.jar LID -all test/news4L-500each.tsv.gz -split 0.15 -iterations 10
  ignore should "train a model on 4 newswire languages using 85/15 train/test split" in {
    val args = "-all test/news4L-500each.tsv.gz -split 0.15 -iterations 10"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.983f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "train a model over 11 languages of twitter" in {
    val args = "-all test/twitter-11-500each.tsv.gz -split 0.15 -iterations 10"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.807f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "train a model over 4 languages 5K data per" in {
    val args = "-all test/no_nl_da_en_5k.tsv.gz -split 0.15 -iterations 10"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.889f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "train a model over 4 languages 500 data per" in {
    val args = "-all test/no_nl_da_en_500.tsv.gz -split 0.15 -iterations 10"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.710f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "train a model and test on train - should be 100% accurate" in {
    val args = "-train test/twitter-11-500each.tsv.gz -test test/twitter-11-500each.tsv.gz -iterations 10"
    val expected = 1.000f
    new LID().ep(args.split(" ")) shouldBe expected +- 0.001f
  }

  ignore should "save out a model, log and score files" in {
    val testout = "testOut"
    new File(testout).mkdir()
    val modelFile = testout + File.separator +"news4L.mod"
    val logFile   = testout + File.separator +"news4L.log"
    val scoreFile = testout + File.separator +"news4L.score"
    val args = "-all test/news4L-500each.tsv.gz -split 0.15 -iterations 30 -model " + modelFile + " -log " + logFile + " -score " + scoreFile
    val accuracy = new LID().ep(args.split(" "))

    accuracy shouldBe 0.976f +- 0.001f
    new File(modelFile) should exist
    new File(logFile) should exist
    new File(scoreFile) should exist
  }

  ignore should "score against a saved model" in {
    val args = "-test test/news4L-500each.tsv -model models/news4L.mod"
    val accuracy = new LID().ep(args.split(" "))
    accuracy shouldBe 0.9965f +- 0.001f
  }

  it should "return labels of saved model" in {
    val newsRunner = new mitll.lid.Scorer("models/news4L.mod")
    val labels = newsRunner.getLabels
    labels should contain only ("fa", "dar", "es", "ru")
  }

  ignore should "score against a model" in {
    val newsRunner = new mitll.lid.Scorer("models/news4L.mod")
    val test = "what language is this text string?"
    var (language, confidence) = newsRunner.textLID(test)
    val formatted = f"$confidence%1.2f"
    println(s"'$test' = $language with confidence $formatted")
  }

  ignore should "score against a model returning all scores" in {
    val newsRunner = new mitll.lid.Scorer("models/news4L.mod")
    val allScores = newsRunner.textLIDFull("what language is this text string?")
    allScores.foreach { p =>
      val score = p._2
      val formatted = f"$score%1.2f"
      println(p._1.name + " = " + formatted)
    }
  }

  ignore should "score against a model returning top 2 scores" in {
    val newsRunner = new mitll.lid.Scorer("models/news4L.mod")
    val allScores = newsRunner.textLIDTopN("what language is this text string?", 2)
    allScores.foreach { p =>
      val score = p._2
      val formatted = f"$score%1.2f"
      println(p._1.name + " = " + formatted)
    }
  }

  ignore should "show usage" in {
    LLClass.main(Array())
  }
}
