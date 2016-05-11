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

class TwitterEvalSpec extends FlatSpec with Matchers with LazyLogging {
  ignore should "train a model over recall corpus and save it" in {
    val args = "-all test/recallCorpus.tsv -split 0.15 -iterations 10 -stratify false -dropSmallerThan 500 -model models/tweetRecall.mod -log logs/recall.log"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.9246396f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "train a model over recall normalized corpus and save it" in {
    val args = "-all test/recallCorpusNorm.tsv -split 0.15 -iterations 10 -stratify false -dropSmallerThan 500 -model models/tweetRecallNorm.mod -log logs/recallNorm.log"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.923f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "train a model over precision norm corpus and save it" in {
    val args = "-all test/precCorpusNorm.tsv -split 0.15 -iterations 10 -stratify false -dropSmallerThan 500 -model models/tweetPrecisionNorm.mod -log logs/precisionNorm.log"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.868f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "train a model over precision norm corpus skip und labels and save it" in {
    val args = "-all test/precCorpusNormNoUnd.tsv -split 0.15 -iterations 10 -stratify false -dropSmallerThan 500 -model models/tweetPrecisionNormNoUnd.mod -log logs/precisionNormNoUnd.log"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.95504534f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "train a model over precision corpus and save it" in {
    val args = "-all test/precisionCorpus.tsv -split 0.15 -iterations 10 -stratify false -dropSmallerThan 500 -model models/tweetPrecision.mod -log logs/precision.log"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.85813046f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "train a model over precision corpus skip und labels and save it" in {
    val args = "-all test/precisionCorpusNoUnd.tsv -split 0.15 -iterations 10 -stratify false -dropSmallerThan 500 -model models/tweetPrecisionNoUnd.mod -log logs/precisionNoUnd.log"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.9488713f
    overallAccuracy shouldBe expected +- 0.001f
  }

  ignore should "train a model over uniform corpus and save it" in {
    val args = "-all test/uniformCorpus.tsv -split 0.15 -iterations 10 -stratify false -dropSmallerThan 500 -model models/tweetUniform.mod -log logs/uniform.log"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.91174674f
    overallAccuracy shouldBe expected +- 0.001f
  }

  it should "train a model over uniform corpus and save it and skip und label" in {
    val args = "-all test/uniformCorpusNoUnd.tsv -split 0.15 -iterations 10 -stratify false -dropSmallerThan 500 -model models/tweetUniform.mod -log logs/uniformNoUnd.log"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.91174674f
    overallAccuracy shouldBe expected +- 0.001f
  }

  it should "train a model over uniform normalized corpus and save it and skip und label" in {
    val args = "-all test/uniformCorpusNormNoUnd.tsv -split 0.15 -iterations 10 -stratify false -dropSmallerThan 500 -model models/tweetUniformNorm.mod  -log logs/uniformNormNoUnd.log"
    val overallAccuracy = new LID().ep(args.split(" "))
    val expected = 0.9291058f
    overallAccuracy shouldBe expected +- 0.001f
  }
}
