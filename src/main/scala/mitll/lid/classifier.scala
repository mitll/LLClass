
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

import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.{ArrayBuffer, Map}

// [TODO] need a library interface that allows:
// abstract class Classifier(val model : LinearModel) {
//   def train[I](labelledFeatureExtractor : (I) => (FV, Symbol), );
//   def regress[I](labelledFeatureExtractor : (I) => (FV, Float));
//   def classify(fv : FV) : Array[(Double, Symbol)];
// }

trait Classifier[I] {
  val fExtractor: Function1[I, FV]

  def classify(input: I): Array[(Double, Symbol)]

  def regress(input: I): Double
}

trait ClassifierFactory[I] {
  def getClassifier(dir: String): Classifier[I]
}

trait TrainerTemplate extends InternalPipeSupport with LazyLogging {
  var (parts, trainSet, all, split, modelfn, testSet, scorefn, abname, tokens, merger, parScore, stratify: Boolean, dropSmallerThan: Int) = (1, "", "", 0.1f, "", "", "", "", "", "", false, true, 0)
  var (trainAlg, average, iter, kBest, slack, svmC, gamma) = ("mira", true, 20, 4, 0.01f, 1.0f, 0.02f)
  config += "General"   -> Params("log"           -> Arg(logfn _, logfn_= _,       "Log to this file instead of STDERR"),
                                  "parts"         -> Arg(parts _, parts_= _,       "For scoring, split data into these parts"),
                                  "train"         -> Arg(trainSet _, trainSet_= _, "Training set"),
                                  "all"           -> Arg(all _, all_= _,           "A directory containing train and test documents (*.txt/*.tsv) "),
                                  "merger"        -> Arg(merger _, merger_= _,     "A directory with documents in two languages, to be merged before train/test."),
                                  "split"         -> Arg(split _, split_= _,       "proportion (0.0 - 1.0) of 'all' to use as test vectors (per class)"),
                                  "model"         -> Arg(modelfn _, modelfn_= _,   "Model to either save/load"),
                                  "test"          -> Arg(testSet _, testSet_= _,   "Test set"),
                                  "score"         -> Arg(scorefn _, scorefn_= _,   "Output score file"),
                                  "parScore"      -> Arg(parScore _, parScore_= _, "Do scoring in parallel"),
                                  "stratify"      -> Arg(stratify _, stratify_= _, "Stratify the data - require same number of examples per class"),
                                  "dropSmallerThan" -> Arg(dropSmallerThan _, dropSmallerThan_= _, "Drop classes that have less than this number of examples"),
                                  "abname"        -> Arg(abname _, abname_= _,     "Ablation group name"),
                                  "tokens"        -> Arg(tokens _, tokens_= _,     "Tokens dump out directory"))
  config += "Training"  -> Params("train-method"  -> Arg(trainAlg _, trainAlg_= _, "Specifies a training algorithm. Must be either: mira, perceptron, or svm"),
                                  "average"       -> Arg(average _, average_= _,   "For online methods, average model parameters between iterations"),
                                  "iterations"    -> Arg(iter _, iter_= _,         "Number of iterations of training"))
  config += "MIRA"      -> Params("k-best"        -> Arg(kBest _, kBest_= _,       "Number of k-best hypotheses to consider for the margin approximation"),
                                  "slack"         -> Arg(slack _, slack_= _,       "Update threshold for per instance weight updates"))
  config += "SVM"       -> Params("c"             -> Arg(svmC _, svmC_= _,         "margin tradeoff parameter"),
                                  "gamma"         -> Arg(gamma _, gamma_= _,       "svm gamma parameter"))

  // -------------------------------------------------------------------------------------------------------------------------------------
  // Trainer helpers
  // -------------------------------------------------------------------------------------------------------------------------------------
  def modelTrainer: Function2[Array[Array[Double]], Array[Symbol], LinearModel] = trainAlg match {
    case "mira" => (w, l) => new MIRA(w, l, kBest, slack)
    case "svm" => (w, l) => new SVM(w, l, svmC, gamma)
    case "svmopen" => (w, l) => new SVMOPEN(w, l, svmC, gamma)
    case "perceptron" => (w, l) => new Perceptron(w, l)
    case _ => throw Fatal("unkown trainer type")
  }

  def splitLabelledData[T](set: Map[Symbol, ArrayBuffer[(T, Symbol)]], splitRatio: Float): (List[(T, Symbol)], List[(T, Symbol)]) = {
    val skeys = set.keys.toList.sortWith(_.name > _.name)
    if (splitRatio > 0) // take from the beginning
      skeys.flatMap { lab => set(lab).drop(math.round(set(lab).length * splitRatio)) } -> skeys.flatMap { lab => set(lab).take(math.round(set(lab).length * splitRatio)) }
    else // take from the end
      skeys.flatMap { lab => set(lab).take(math.round(set(lab).length * (1.0f + splitRatio))) } -> skeys.flatMap { lab => set(lab).drop(math.round(set(lab).length * (1.0f + splitRatio))) }
  }

  def datasetBreakdown[T](set: Map[Symbol, ArrayBuffer[(T, Symbol)]]) {
    val skeys = set.keys.toList.sortWith(_.name > _.name)
    log("DEBUG", "sorted keys (" + skeys.size + ")= " + skeys)
    log("INFO", "Dataset breakdown")
    log.separator()
    var total = 0
    for (label <- skeys) {
      val vecs = set(label); total += vecs.length; log("INFO", "%-20s | %10d", label.name, vecs.length)
    }
    log.separator()
    log("INFO", "%-20s | %10d", "Total", total)
  }

  // Make sure we take no more than the minimum class count from each class
  def stratifyDataset[T](set: Map[Symbol, ArrayBuffer[(T, Symbol)]]): Map[Symbol, ArrayBuffer[(T, Symbol)]] = {
    val smallest = set.values.map(_.length).min
    set.map(pair => pair._1 -> pair._2.take(smallest))
  }
}
