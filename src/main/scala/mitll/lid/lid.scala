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
import java.net.URLEncoder

import com.typesafe.scalalogging._
import mitll.lid.utilities._

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, Map}
import scala.collection.parallel.ParIterable
import scala.io.{BufferedSource, Source}
import scala.{Iterable, Seq}
import scalaj.http._

object LLClass extends LazyLogging {
  def main(args: Array[String]) {
    if (args.length < 2) {
      System.err.println("Usage : expecting args like : LID -all test/original4.tsv.gz")
    }
    else {
      var run = args(0)
      if (run == "multiparam") {
        new multiparam().main(args.slice(1, args.length))
      }
      else if (run == "LID") {
        new LID().main(args.slice(1, args.length))
      }
      else {
        logger.error("Usage : expecting first arg to be either multiparam or LID but got " + run)
      }
    }
  }
}

class Scorer(val modeldir: String) extends LazyLogging {
  val runner = new LID
  val before = System.currentTimeMillis()
  val lidModel = runner.getClassifier(modeldir.getAbsolutePath)
  val now = System.currentTimeMillis()
  logger.debug("took " + (now - before) + " millis to load model")

  def textLID(text: String): (String, Double) = {
    if (text != null && text != "") {
      val classify1: Array[(Double, Symbol)] = lidModel.classify(text)
      val firstResult: (Double, Symbol) = classify1(0)
      var language = firstResult._2.name

      val lnum = math.exp(firstResult._1)
      val denom = classify1.foldLeft(0.0) { (a, b) => a + math.exp(b._1) }
      val conf = (lnum / denom) * 100
      // println(s"$text = $firstResult or $lnum / $denom")
      val answers = (language, conf)
      answers
    } else ("error: empty text string", 0.0)
  }

  def textLIDFull(text: String): Array[(Symbol, Double)] = {
    if (text != null && text != "") {
      val classify1: Array[(Double, Symbol)] = lidModel.classify(text)
      val map: Array[(Symbol, Double)] = classify1.map { case (k, v) => (v, k) }
      map
    } else {
      Array()
    }
  }

  def textLIDTopN(text: String, n: Int): Array[(Symbol, Double)] = {
    textLIDFull(text).take(n)
  }

  def textLIDTop2(text: String) = textLIDTopN(text, 2)
}

class multiparam {
  def sweepSVM(data: String, exp_name: String) {
    var coarseC: IndexedSeq[Double] = (-5 to 16) map {
      math.pow(2, _)
    }
    var coarseGamma = (-5 to 6) map {
      math.pow(2, _)
    }
    for (bkgmin <- Seq.range(2, 21, 1).par) {
      for (g <- coarseGamma.par) {
        for (C <- coarseC.par) {
          for (wOrder <- Seq.range(1, 5, 1).par) {
            for (cOrder <- Seq.range(1, 5, 1).par) {
              var c = new LID
              var title: String = "svm_sweep_results/" + exp_name + "_" + bkgmin + "_" + g + "_" + C + "_" + wOrder + "_" + cOrder
              val params = "-train-method svm -all " + data + " -bkg-min-count " + bkgmin + " -split 0.20 -gamma " + g + " -c " + C + " -log " + title + " -word-ngram-order " + wOrder + " -char-ngram-order " + cOrder
              val params2: Array[String] = params.split(" ")
              c.main(params2)
            }
          }
        }
      }
    }
  }

  def sweepMIRA(data: String, exp_name: String) {
    for (iterations <- Seq.range(5, 30, 5).par) {
      for (bkgmin <- Seq.range(2, 21, 1).par) {
        for (slack <- Seq.iterate(0.0005, 25)(0.00025 +).par) {
          for (wOrder <- Seq.range(1, 5, 1).par) {
            for (cOrder <- Seq.range(1, 5, 1).par) {
              var c = new LID
              var title: String = "mira_sweep_results/" + exp_name + "_" + iterations + "_" + slack + "_" + bkgmin + "_" + wOrder + "_" + cOrder
              var params = "-train-method mira -all " + data + " -iterations " + iterations + " -slack " + slack + " -bkg-min-count " + bkgmin + " -split 0.20 -log " + title + " -word-ngram-order " + wOrder + " -char-ngram-order " + cOrder
              var params2: Array[String] = params.split(" ")
              c.main(params2)
            }
          }
        }
      }
    }
  }

  def runSVM(data: String, exp_name: String) {
    var c = new LID
    var title: String = "run_svm_results/" + exp_name + "_default"
    val params = "-train-method svm -all " + data + " -split 0.20 -log " + title
    val params2: Array[String] = params.split(" ")
    c.main(params2)
  }

  def runMIRA(data: String, exp_name: String) {
    var c = new LID
    var title: String = "run_mira_results/" + exp_name + "_default"
    var params = "-train-method mira -all " + data + " -split 0.20 -log " + title
    var params2: Array[String] = params.split(" ")
    c.main(params2)
  }

  def main(args: Array[String]) {
    var method = args(0)
    var data = args(1)
    var exp_name = args(2)
    if (method == "sweep_svm") {
      sweepSVM(data, exp_name)
    }
    if (method == "sweep_mira") {
      sweepMIRA(data, exp_name)
    }
    if (method == "run_svm") {
      runSVM(data, exp_name)
    }
    if (method == "run_mira") {
      runMIRA(data, exp_name)
    }
  }
}

class LID extends InternalPipeRunner[Float] with TrainerTemplate with ClassifierFactory[String] {
  // -------------------------------------------------------------------------------------------------------------------------------------
  // Data formats/readers
  // -------------------------------------------------------------------------------------------------------------------------------------
  val LabelText =
    """(?s)^(\S+)\t+(.*)$""".r

  val LabelTextSpace =
    """^\s*(\S+)\s+(.*)$""".r

  val skipLabels = (labeltext: (String, Symbol)) => labeltext._1

  def labelledFileInput(fn: String) = FileLines(fn).zipWithIndex.map {
    case (LabelText(label, text), idx) => text -> Symbol(label);
    case (LabelTextSpace(label, text), idx) => text -> Symbol(label);
    case (text@_, idx) => throw Fatal("line " + idx + ": '" + text + "' doesn't match expected format of label followed by text!");
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  // LID specific preprocessing
  // -------------------------------------------------------------------------------------------------------------------------------------
  def wcNgrams(corder: Int, worder: Int) = {
    val cn = charTokenizer * ngrams2(maxOrder = corder)
    val wn = wordTokenizer(defaultSpace) * ngrams2(maxOrder = worder)
    (s: String) => cn.on1(s) ++ wn.on1(s);
  }

  def preprocess(corder: Int = 3, worder: Int = 1) = cleanSpace() * wcNgrams(corder = corder, worder = worder)

  //ngrams
  def docExtractor(bkg: FV, dict: Map[Symbol, Int], corder: Int, worder: Int, cutoff: Float) =
    preprocess(corder, worder) * countTokens2 _ * counts2fv(dictionary = dict, unk = true) * norm(bkg = bkg, cutoff = cutoff)

  // -------------------------------------------------------------------------------------------------------------------------------------
  // ClassifierFactory
  // -------------------------------------------------------------------------------------------------------------------------------------
  def getClassifier(fn: String): Classifier[String] = {
    assert(fn.existe, "Can't find LID model file: " + fn)

    // this section of code needs to be changed in order to load existing models
    // and avoid serialUID errors when using different versions of java
    withObjectInput(fn) { f =>
      val (cOrder, wOrder, cutoff) = (f.readObject().asInstanceOf[Int], f.readObject().asInstanceOf[Int], f.readObject().asInstanceOf[Float])
      val (dict, index) = f.readObject().asInstanceOf[Map[Symbol, Int]] -> f.readObject().asInstanceOf[Array[Symbol]]
      val (bkg, lm) = FV.obj(f) -> f.readObject().asInstanceOf[LinearModel]

      new Classifier[String] {
        val fExtractor = docExtractor(bkg, dict, cOrder, wOrder, cutoff).on1 _

        def classify(input: String): Array[(Double, Symbol)] = lm.csortScores(fExtractor(input))

        def knownClasses = lm.classIndex.keySet.toSeq

        def regress(input: String) = throw Fatal("Regression not supported for discrete class problems")
      }
    }
  }

  // Use langid as a scoring service
  // assumes we've downloaded and installed langid.py : https://github.com/saffsd/langid.py
  // and it's running as a webservice at http://172.25.180.54:9008
  def getLangidClassifier = new Classifier[String] {
    val fExtractor = null

    def regress(input: String) = throw Fatal("Regression not supported for discrete class problems")

    // I should probably use a json library...
    override def classify(input: String): Array[(Double, Symbol)] = {
      val enc = URLEncoder.encode(input)
      val url: String = "http://172.25.180.54:9008/detect?q=\"" + enc + "\""

      val html = Source.fromURL(url)
      val s = html.mkString
      val split = s.split("language\": \"")
      val split2 = s.split("confidence\": ")

      val tail = split(1)
      var resp = tail.split("\"").head
      if (resp.endsWith(".txt")) resp = resp.dropRight(4)
      val conf = split2(1).split("\"").head.split(",").head
      val dv = conf.toDouble
      Array(dv -> Symbol(resp))
    }

    override def knownClasses: Seq[Symbol] = Seq()
  }

  def getTestRESTClassifier = new Classifier[String] {
    val fExtractor = null

    def regress(input: String) = throw Fatal("Regression not supported")

    // I should probably use a json library...
    override def classify(input: String): Array[(Double, Symbol)] = {

//      val result = Http("http://localhost:8080/classifyJSON").postData(input)
//        .header("Content-Type", "application/json")
//        .header("Charset", "UTF-8")
//        .option(HttpOptions.readTimeout(10000)).asString

      var enc = URLEncoder.encode(input)
      if (enc.length > 2000) {
        logger.warn("truncated " + input)
        enc = enc.substring(0, 2000)
      }
     val url = "http://localhost:8080/classifyJSON?q=" + enc

      try {
        val html: BufferedSource = Source.fromURL(url)
        val s = html.mkString
        val tail = s.split("class")(1)
        var resp = tail.split("\" : \"").tail.head.split("\"").head
        if (resp.endsWith(".txt")) resp = resp.dropRight(4)
        val split3 = s.split("confidence")(1)
        val conf = split3.split("\" : \"").tail.head.split("\"").head
        val dv = conf.toDouble
        Array(dv -> Symbol(resp))
      } catch {
        case e:Exception =>
          logger.error("Got " + e.getMessage + " for " + input.length + " : " + input)
          Array(-1d -> Symbol("too long"))
      }
    }

    override def knownClasses: Seq[Symbol] = Seq()
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  // Standalone runner, config and helpers
  // -------------------------------------------------------------------------------------------------------------------------------------
  val program = "lid-test"
  var (wOrder, cOrder, minCount, prune, cutoff) = (1, 3, 2, 0.0f, 1e10f)

  config += "Front End" -> Params("bkg-min-count" -> Arg(minCount _, minCount_= _, "Minimum count for background models"),
    "bkg-prune" -> Arg(prune _, prune_= _, "Prune features with p < this value from the background model"),
    "norm-cutoff" -> Arg(cutoff _, cutoff_= _, "After normalization, force normalized feature to be max(norm-cutoff, feature-value)"),
    "word-ngram-order" -> Arg(wOrder _, wOrder_= _, "Word N-gram order for feature extraction"),
    "char-ngram-order" -> Arg(cOrder _, cOrder_= _, "Character N-gram order for feature extraction"))

  def splitData: (List[(String, Symbol)], List[(String, Symbol)]) = {
    if (all != "") {
      val set = mutable.HashMap[Symbol, ArrayBuffer[(String, Symbol)]]()
      labelledFileInput(all) foreach {
        case (text, label) =>
          if (!set.isDefinedAt(label)) set(label) = ArrayBuffer[(String, Symbol)]()
          set(label) += text -> label;
      }
      datasetBreakdown(set)

      val enoughData = set.filter(_._2.length >= dropSmallerThan)
      if (enoughData.size < set.size) {
        val pruned = set.keySet.diff(enoughData.keySet)
        logger.info("pruned labels " + pruned.map(_.name).mkString(",") + " since examples less than " + dropSmallerThan)
      }
      val strat = if (stratify) stratifyDataset(enoughData) else enoughData

      datasetBreakdown(strat)
      splitLabelledData(strat, split)
    }
    else Tuple2(null, null)
  }

  def trainModel(trainer: (Array[Array[Double]], Array[Symbol]) => LinearModel, trainSplit: List[(String, Symbol)]): String = {
    logger.info("Starting training...")

    val input = if (trainSet.existe) labelledFileInput(trainSet) else trainSplit
    val labels = input.map(_._2) toList
    val prepped = input |>: skipLabels * preprocess(cOrder, wOrder)
    val ((dict, index, bkgmodel), x) = prepped =+>: bkgS(mincount = minCount, prune = prune)

    // save dict to file for printing heavy hitters (mira.scala 178 - commented out)
    //        var fname = "id_my_mturk_raw"
    //        var outfile = "dicts/"+fname+".txt"
    //        val pw = new PrintWriter(new File(outfile))
    //        for (i <- 0 to (index.length-1)) {
    //          var outstring = i + " " + index(i) + "\n"
    //          pw.write(outstring)
    //        }
    //        pw.close

    val vectors = prepped |>: countTokens2 _ * counts2fv(dict, unk = true) * norm(bkg = bkgmodel, cutoff = cutoff) toList

    val l = vectors.length
    val d = index.length
    logger.info(s"There are $l vectors in training")
    logger.info(s"There are $d in the feature space")
    val before = System.currentTimeMillis()
    val (model, v) = (vectors zip labels) =+>: train(index.length, trainer, average, iter)
    val after = System.currentTimeMillis()

    // save
    val outfn =
      if (modelfn == "") {
        val tf = java.io.File.createTempFile("tmp", "mod")
        tf.deleteOnExit()
        tf.getAbsolutePath
      }
      else modelfn

    logger.info("Training complete in " + (after - before) / 1000 + " seconds")

    // this section of code needs to be serialized to avoid serialUID errors
    // when loading up models with different java versions
    withObjectOutput(outfn) { f =>
      f.writeObject(cOrder)
      f.writeObject(wOrder)
      f.writeObject(cutoff)
      f.writeObject(dict)
      f.writeObject(index)
      bkgmodel.writeObj(f)
      model.save(f);
    }
    outfn
  }

  def parScoreModel(classifier: Classifier[String], testSplit: List[(String, Symbol)]): Float = {
    val before = System.currentTimeMillis()

    val input: Iterable[(String, Symbol)] = if (testSet.existe) labelledFileInput(testSet) else testSplit
    val labels: List[Symbol] = input.map(_._2) toList
    val beforeC = System.currentTimeMillis()

    val parinput = input.par
    val scores: ParIterable[(Array[(Double, Symbol)], Symbol)] = parinput.map(doc => classifier.classify(doc._1) -> doc._2)
    val scoresSeq: Iterable[(Array[(Double, Symbol)], Symbol)] = scores.seq

    val (score, confmat) = scoreClassification(scoresSeq)

    val afterC = System.currentTimeMillis()
    logger.debug("took " + (afterC - beforeC) + " millis to classify " + labels.length + " items")

    val after = System.currentTimeMillis()
    logger.debug("took " + (after - before) + " millis to score " + labels.length + " items")

    log("INFO", "# of trials: " + labels.length)
    for (c <- confmat) log("INFO", c)
    log("INFO", "accuracy = %f", score)
    if (scorefn != "")
      withPrint(scorefn) { f =>
        for (((text, label), scores) <- input.toList zip scoresSeq)
          f.println("%s %s ::: %s" %(label.name, text, scoresSeq.map { case (sc, lab) => lab.name + " -> " + sc }.mkString(" ")));
      }
    score
  }

  def scoreModel(classifier: Classifier[String], testSplit: List[(String, Symbol)]): Float = {
    val input = if (testSet.existe) labelledFileInput(testSet) else testSplit
    val labels = input.map(_._2) toList
    val beforeC = System.currentTimeMillis()

    val scores = input.map(doc => classifier.classify(doc._1))
    val (score, confmat) = scoreClassification(scores zip labels)
    val afterC = System.currentTimeMillis()
    logger.debug("scoreModel took " + (afterC - beforeC) + " millis to classify " + labels.length + " items")

    log("INFO", "# of trials: " + labels.length)
    for (c <- confmat) log("INFO", c)
    log("INFO", s"accuracy = $score")
    if (scorefn != "")
      withPrint(scorefn) { f =>
        for (((text, label), scores) <- input.toList zip scores)
          f.println("%s %s ::: %s" %(label.name, text, scores.map { case (sc, lab) => lab.name + " -> " + sc }.mkString(" ")));
      }
    score
  }

  // main
  // if given one set of data to train and test on, split it first
  // if no model given, train one and use it
  // finally, score the model
  def run(args: Array[String]): Float = {
    //    assert(all != "")
    if (!all.isEmpty && !all.existe) {
      log("ERROR", "Can't find " +
        "data file at " + new File(all).getAbsolutePath)
      return -1
    }

    val (trainSplit, testSplit) = splitData

    if (!trainSet.isEmpty && !trainSet.existe) {
      log("WARN", s"Can't find training set at $trainSet")
    }

    val classifier = if (trainSet.existe || trainSplit != null) {
      val outfn = trainModel(modelTrainer, trainSplit)
      getClassifier(outfn); // prep the classifier for running examples
    }
    else {
      if (modelfn.isEmpty) {
        log("WARN", "Expecting model file parameter or train param")
        null
      }
      else {
        getClassifier(modelfn)
      }
    }

    // classify

    if (classifier != null) {
      if (testSet.existe) {
        logger.info(s"Scoring test set $testSet")
      }
      else if (testSet.nonEmpty) {
        logger.warn(s"Can't find test set $testSet")
      }
      if (testSet.existe || testSplit != null) {
        if (parScore) {
          parScoreModel(classifier, testSplit)
        }
        else {
          scoreModel(classifier, testSplit)
        }
      }
      else 0
    }
    else 0
  }

  def testLangid(testfile: String): Float = {
    val classifier = getLangidClassifier
    testSet = testfile
    if (classifier != null) {
      if (testSet.existe) {
        if (parScore) {
          parScoreModel(classifier, null)
        }
        else {
          scoreModel(classifier, null)
        }
      }
      else 0
    }
    else 0
  }

  def testREST(testfile: String): Float = {
    val classifier = getTestRESTClassifier
    testSet = testfile
    if (classifier != null) {
      if (testSet.existe) {
        scoreModel(classifier, null)
      }
      else 0
    }
    else 0
  }
}
