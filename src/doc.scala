package mitll

import utilities._
import collection.mutable.{ArrayBuffer, ListBuffer, HashMap, Map};
import text._

object DOC extends InternalPipeRunner[Unit] with TrainerTemplate with ClassifierFactory[String] {
  // -------------------------------------------------------------------------------------------------------------------------------------
  // Data formats/readers
  // -------------------------------------------------------------------------------------------------------------------------------------
  val LabelText = """(?s)^(\S+)\t+(.*)$""".r;
  val skipLabels = (labeltext : (String, Symbol)) => labeltext._1;
  def labelledFileInput(fn : String) = FileLines(fn).zipWithIndex.map { 
    x => x match {
      case (LabelText(label, text), idx) => text -> Symbol(label); 
      case (text @ _, idx) => throw Fatal("line " + idx + ": '" + text + "' doesn't match!");
    }
  }
  
  // -------------------------------------------------------------------------------------------------------------------------------------
  // DOC specific preprocessing
  // -------------------------------------------------------------------------------------------------------------------------------------
  def wcNgrams(corder : Int, worder : Int) = {
    val cn = charTokenizer * ngrams2(maxOrder = corder);
    val wn = wordTokenizer(defaultSpace) * ngrams2(maxOrder = worder);
    (s : String) => cn.on1(s) ++ wn.on1(s);
  }
  def preprocess(corder : Int = 3, worder : Int = 1) = cleanSpace() * wcNgrams(corder = corder, worder = worder); //ngrams 
  def docExtractor(bkg : FV, dict : Map[Symbol, Int], corder : Int, worder : Int, cutoff : Float) = 
    preprocess(corder, worder) * countTokens2 _ * counts2fv(dictionary = dict, unk = true) * norm(bkg = bkg, cutoff = cutoff);

  // -------------------------------------------------------------------------------------------------------------------------------------
  // ClassifierFactory
  // -------------------------------------------------------------------------------------------------------------------------------------
  def getClassifier(fn : String) = {
    assert(fn.existe, "Can't find DOC model file: " + fn);

    // this section of code needs to be changed in order to load existing models
    // and avoid serialUID errors when using different versions of java
    withObjectInput(fn) { f => 
      val (cOrder, wOrder, cutoff) = (f.readObject().asInstanceOf[Int], f.readObject().asInstanceOf[Int], f.readObject().asInstanceOf[Float]);
      val (dict, index)   = f.readObject().asInstanceOf[Map[Symbol, Int]] -> f.readObject().asInstanceOf[Array[Symbol]];
      val (bkg, lm)       = FV.obj(f) -> f.readObject().asInstanceOf[LinearModel];

      new Classifier[String] {
        val fExtractor = docExtractor(bkg, dict, cOrder, wOrder, cutoff).on1 _;
        def classify(input : String) = lm.csortScores(fExtractor(input))
        def regress(input : String) = throw Fatal("Regression not suppored for discrete class problems");
      }
    }
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  // Standalone runner, config and helpers
  // -------------------------------------------------------------------------------------------------------------------------------------
  val program = "doc-test";
  var (wOrder, cOrder, minCount, prune, cutoff) = (1, 3, 2, 0.0f, 1e10f);

  config += ("Front End") -> Params("bkg-min-count"      -> Arg(minCount _, minCount_= _, "Minimum count for background models"),
                                    "bkg-prune"          -> Arg(prune _, prune_= _,       "Prune features with p < this value from the background model"),
                                    "norm-cutoff"        -> Arg(cutoff _, cutoff_= _,     "After normalization, force normalized feature to be max(norm-cutoff, feature-value)"),
                                    "word-ngram-order"   -> Arg(wOrder _, wOrder_= _,     "Word N-gram order for feature extraction"),
                                    "char-ngram-order"   -> Arg(cOrder _, cOrder_= _,     "Character N-gram order for feature extraction"));

//  def run(args : Array[String]) {}

  // main
  def run(args : Array[String]) {
    assert(all != "" || trainSet != "" || modelfn != "" || merger!= "");
    val (trainSplit, testSplit) =
      if (all != "") {
        val set = HashMap[Symbol, ArrayBuffer[(String, Symbol)]]();
        labelledFileInput(all) foreach { case (text, label) => 
          if (!set.isDefinedAt(label)) set(label) = ArrayBuffer[(String, Symbol)]();
          set(label) += text -> label;
        }
        datasetBreakdown(set);
        splitLabelledData(set, split);
      }
      else Tuple2(null, null);
    val trainer = modelTrainer;

    val classifier =
      if (trainSet.existe || trainSplit != null) {
        log("INFO", "Starting training...");
        
        val input   = if (trainSet.existe) labelledFileInput(trainSet) else trainSplit;
        val labels  = input.map(_._2) toList;
        val prepped = input |>: skipLabels * preprocess(cOrder, wOrder);        
        val ((dict, index, bkgmodel), x) = prepped =+>: bkgS(mincount = minCount, prune = prune);
        val vectors                      = prepped |>:  countTokens2 _ * counts2fv(dict, unk = true) * norm(bkg = bkgmodel, cutoff = cutoff) toList;
        
        log("INFO", "There are %d vectors in training", vectors.length);
        log("INFO", "There are %d dimensions in the feature space", index.length);
        val (model, v) = (vectors zip labels) =+>: train(index.length, trainer, average, iter);
        
        val outfn = 
          if (modelfn == "") { val tf = java.io.File.createTempFile("tmp", "mod"); tf.deleteOnExit(); tf.getAbsolutePath; }
          else modelfn;
        
        log("INFO", "Training complete.");
        
        // this section of code needs to be serialized to avoid serialUID errors
        // when loading up models with different java versions
        withObjectOutput(outfn) { f =>
          f.writeObject(cOrder); f.writeObject(wOrder); f.writeObject(cutoff);
          f.writeObject(dict); f.writeObject(index);
          bkgmodel.writeObj(f); model.save(f);
        }

        getClassifier(outfn); // prep the classifier for running examples
      }
      else getClassifier(modelfn);

    // classify
    if (testSet.existe || testSplit != null) {
      val input            = if (testSet.existe) labelledFileInput(testSet) else testSplit;
      val labels           = input.map(_._2) toList;
      val scores           = input.map(doc => classifier.classify(doc._1));
      val (score, confmat) = scoreClassification(scores zip labels);
      
      log("INFO", "# of trials: " + labels.length);
      for (c <- confmat) log("INFO", c);
      log("INFO", "accuracy = %f", score);
      if (scorefn != "")
        withPrint(scorefn) { f =>
          for (((text, label), scores) <- input.toList zip scores)
            f.println("%s %s ::: %s" % (label.name, text, scores.map { case (sc, lab) => lab.name + " -> " + sc }.mkString(" ")));
        }
    }
  }
}
