/*
 *  Written by Wade Shen <swade@ll.mit.edu>
 *  Copyright 2005-2016 Massachusetts Institute of Technology, Lincoln Laboratory
 *  Revision: 0.2
 */

package mitll

import java.io.File

import mitll.utilities._

import scala.collection.mutable.{ArrayBuffer, HashMap, Map}


object MITLL_LID {
  def main(args: Array[String]) {
    if (args.length < 2) {
      System.err.println("Usage : expecting args like : LID -all test/original4.tsv.gz")
    }
    else {
      var run = args(0)
      if (run == "multiparam") {
        var c = new multiparam
        c.main(args.slice(1, args.length))
      }
      else if (run == "LID") {
        var c = new LID
        c.main(args.slice(1, args.length))
      }
      else {
        System.err.println("Usage : expecting first arg to be either multiparam or LID but got " + run)
      }
    }
  }
}

class SCORE(val modeldir: String) {
  val runner = new LID
  val lidModel = runner.getClassifier(modeldir.getAbsolutePath)

  def textLID(text: String): (String, Double) = {
    if (text != null && text != "") {
      var language = lidModel.classify(text)(0)._2.name
      val lnum = math.exp(lidModel.classify(text)(0)._1)
      val denom = lidModel.classify(text).foldLeft(0.0) { (a, b) => a + math.exp(b._1) }
      val conf = (lnum / denom) * 100
      val answers = (language, conf)
      answers
    } else ("error: empty text string", 0.0)
  }

  def textLIDFull(text: String): Array[(Double, Symbol)] = {
    if (text != null && text != "") {
      var result = lidModel.classify(text)
      result
    } else {
      Array[(Double, Symbol)]()
    }
  }
}

class multiparam {
  def sweepSVM(data: String, exp_name: String) {
    var coarseC = (-5 to 16) map {
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

object ParseTest {
  val test:String = " es @AnderDelPozo @PesqueWhite hahaha yo tambien me he quedao pillao ahahha"
  val test2:String = "es @AnderDelPozo @PesqueWhite hahaha yo tambien me he quedao pillao ahahha"
  val test3:String = "es\t@AnderDelPozo @PesqueWhite hahaha yo tambien me he quedao pillao ahahha"

  // -------------------------------------------------------------------------------------------------------------------------------------
  val LabelText =
    """(?s)^(\S+)\t+(.*)$""".r

  val LabelTextSpace =
    """^\s*(\S+)\s+(.*)$""".r

  def labelled(test:String) : (String,Symbol) = {
    val ret = test match {
      case (LabelText(label, text)) => text -> Symbol(label)
      case (LabelTextSpace(label, text)) => text -> Symbol(label)
      case _  => "bad" -> Symbol("bad")
    }
    ret
  }

  def main(args:Array[String]):Unit = {
    val ret =  labelled(test)
    System.out.println("got " + ret)

    System.out.println("got " + labelled(test2))
    System.out.println("got " + labelled(test3))
  }


}

class LID extends InternalPipeRunner[Unit] with TrainerTemplate with ClassifierFactory[String] {
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
  def getClassifier(fn: String) = {
    assert(fn.existe, "Can't find LID model file: " + fn)

    // this section of code needs to be changed in order to load existing models
    // and avoid serialUID errors when using different versions of java
    withObjectInput(fn) { f =>
      val (cOrder, wOrder, cutoff) = (f.readObject().asInstanceOf[Int], f.readObject().asInstanceOf[Int], f.readObject().asInstanceOf[Float])
      val (dict, index) = f.readObject().asInstanceOf[Map[Symbol, Int]] -> f.readObject().asInstanceOf[Array[Symbol]]
      val (bkg, lm) = FV.obj(f) -> f.readObject().asInstanceOf[LinearModel]

      new Classifier[String] {
        val fExtractor = docExtractor(bkg, dict, cOrder, wOrder, cutoff).on1 _

        def classify(input: String) = lm.csortScores(fExtractor(input))

        def regress(input: String) = throw Fatal("Regression not suppored for discrete class problems")
      }
    }
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


  // main
  def run(args: Array[String]) {
    //    assert(all != "")
    val (trainSplit, testSplit) =
      if (all != "") {
        if (!new File(all).exists) {
          log("ERROR", "Can't find file at " + new File(all).getAbsolutePath)
          return
        }
        val set = HashMap[Symbol, ArrayBuffer[(String, Symbol)]]()
        labelledFileInput(all) foreach {
          case (text, label) =>
            if (!set.isDefinedAt(label)) set(label) = ArrayBuffer[(String, Symbol)]()
            set(label) += text -> label;
        }
        datasetBreakdown(set)
        val strat = stratifyDataset(set)
        datasetBreakdown(strat)
        splitLabelledData(strat, split)
      }
      else Tuple2(null, null)
    val trainer = modelTrainer

//    log("INFO", s"trainSet $trainSet trainSplit $trainSplit")

    if (!trainSet.isEmpty && !trainSet.existe) log("WARN",s"Can't find training set at $trainSet")
    val classifier =
      if (trainSet.existe || trainSplit != null) {
        log("INFO", "Starting training...")

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

        log("INFO", "There are %d vectors in training", vectors.length)
        log("INFO", "There are %d dimensions in the feature space", index.length)
        val (model, v) = (vectors zip labels) =+>: train(index.length, trainer, average, iter)

        // save
        val outfn =
          if (modelfn == "") {
            val tf = java.io.File.createTempFile("tmp", "mod")
            tf.deleteOnExit()
            tf.getAbsolutePath
          }
          else modelfn

        log("INFO", "Training complete.")

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

        getClassifier(outfn); // prep the classifier for running examples
      }
      else {
        if (modelfn.isEmpty) {
          log("WARN","Expecting model file parameter or train param")
          null
        }
        else {
          getClassifier(modelfn)
        }
      }

    // classify

    if (classifier != null) {
      if (testSet.existe) log("INFO",s"Scoring test set $testSet")
      if (testSet.existe || testSplit != null) {
        val input = if (testSet.existe) labelledFileInput(testSet) else testSplit
        val labels = input.map(_._2) toList
        val scores = input.map(doc => classifier.classify(doc._1))
        val (score, confmat) = scoreClassification(scores zip labels)

        log("INFO", "# of trials: " + labels.length)
        for (c <- confmat) log("INFO", c)
        log("INFO", "accuracy = %f", score)
        if (scorefn != "")
          withPrint(scorefn) { f =>
            for (((text, label), scores) <- input.toList zip scores)
              f.println("%s %s ::: %s" %(label.name, text, scores.map { case (sc, lab) => lab.name + " -> " + sc }.mkString(" ")));
          }
      }
    }
  }
}
