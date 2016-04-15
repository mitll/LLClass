/*
 *  Written by Wade Shen <swade@ll.mit.edu>
 *  Copyright 2005-2016 Massachusetts Institute of Technology, Lincoln Laboratory
 *  Revision: 0.2
 */
package mitll
import java.io.{FileInputStream, ObjectOutputStream}

import mitll.utilities._

import scala.collection.mutable.{ArrayBuffer, HashMap}
import scala.util.Sorting._

//import struct._
import java.io._

import libsvm._
import struct.solver.QPSolver
import struct.types.SLFeatureVector

import scala.io.Source


/*********************************************************************************************/
class FLIterable(val files : Array[String]) extends Iterable[(Symbol, FV, Symbol)] {
  class FLIterator extends Iterator[(Symbol, FV, Symbol)] {
    def parse(s : String) : (Symbol, FV, Symbol) = {
      s.trim match {
        case FLIterable.Labfv(name, label, fv) => (Symbol(name), FV(fv), Symbol(label));
        case _ => assert(false, "ERROR: reading features/labels from -- " + s); (unkToken, FV.zero, unkToken);
      }
    }
    def nextFile : Iterator[String] = {
      val r = FileLines(files(f)).iterator
      f += 1
      r
    }
    def next : (Symbol, FV, Symbol) = {
      if (curF == null || !curF.hasNext) curF = nextFile
      parse(curF.next)
    }
    def hasNext : Boolean = 
      if (f < files.length - 1) true 
      else if (f > files.length) false
      else {
        if (curF == null) curF = nextFile
        curF.hasNext
      }

    // data
    var f = 0
    var curF : Iterator[String] = null
  }
  override def iterator : Iterator[(Symbol, FV, Symbol)] = new FLIterator
}

object FLIterable { 
  val Labfv = """^(\S+)\s+(\S+)\s+(.*)$""".r

  def apply(files : String*) : FLIterable = { new FLIterable(files.toArray); }
  def apply(files : Array[String]) : FLIterable = { new FLIterable(files); }
}

class TrainFLIterable(val fl : Iterable[(Symbol, FV, Symbol)]) extends Iterable[(FV, Symbol)] {
  class TI(val fl : Iterator[(Symbol, FV, Symbol)]) extends Iterator[(FV, Symbol)] {
    def next : (FV, Symbol) = { val n = fl.next; (n._2 -> n._3); }
    def hasNext = fl.hasNext
  }
  override def iterator : Iterator[(FV, Symbol)] = new TI(fl.iterator)
}

/*********************************************************************************************/
class WV(val wv : Array[Array[Double]]) {
  def apply(m : Int) = wv(m)

  def mapper(f : (Double, Int, Int) => Double) {
    for (m <- 0 until wv.length; i <- 0 until wv(m).length)
      wv(m)(i) = f(wv(m)(i), m, i)
  }
  def zero { mapper { (x, m, i) => 0.0 } }
  def <<(other : WV) { mapper { (x, m, i) => other(m)(i) } }
  def /=(d : Double) : WV = { mapper { (x, m, i) => x / d }; this; }
  def +=(other : WV) : WV = { mapper { (x, m, i) => x + other(m)(i) }; this; }
}

/*********************************************************************************************/
abstract class LinearModel(var weights : Array[Array[Double]], val classes : Array[Symbol]) extends Serializable {
  def score(features : FV) : Array[Double] = weights.map { wv => features * wv }
  def scoreTarget(target : Symbol, features : FV) : Double = features * weights(classIndex(target))

  def sortScores(features : FV) : Array[(Double, Int)] = {
    val scores = score(features).zipWithIndex
    stableSort(scores, (a : Tuple2[Double, Int], b : Tuple2[Double, Int]) => a._1 > b._1)
    scores
  }
  def csortScores(features : FV) : Array[(Double, Symbol)] = {
    val scores = score(features).zipWithIndex
    stableSort(scores, (a : Tuple2[Double, Int], b : Tuple2[Double, Int]) => a._1 > b._1)
    scores.map { case (a, b) => (a, classes(b)); }
  }
  def best(features : FV) : Symbol = {
    val scores = sortScores(features)
    classes(scores(0)._2)
  }
  def save(f : ObjectOutputStream) { f.writeObject(this); }
  def save(fn : String) { withObjectOutput(fn) { f => save(f); } }
  def train(features : Iterable[(FV, Symbol)], average : Boolean, iterations : Int)(implicit log : Log)

  def regress(features : Iterable[(FV, Symbol)], average : Boolean, iterations : Int)(implicit log : Log) { train(features, average, iterations); }
  val classIndex = HashMap(classes.zipWithIndex : _*)
}

object LinearModel { def apply(fn : String) = withObjectInput(fn) { f => f.readObject().asInstanceOf[LinearModel] } }

/*********************************************************************************************/
class MIRA(w : Array[Array[Double]], c : Array[Symbol], val k : Int, val C : Double) extends LinearModel(w, c) {
  var Wacc = weights.map { x => Array.fill(x.length)(0.0); }
  override def train(features : Iterable[(FV, Symbol)], average : Boolean, iterations : Int)(implicit log : Log) {
    val km = math.min(k, classes.length)
    val b = new Array[Double](km)
    val distvec = new Array[SLFeatureVector](km)
    var len = 0

    for ((fv, c) <- features) len += 1

    for (i <- 0 until iterations) {
      var s = 0
      log("INFO", "Starting training iteration: %d", int2Integer(i))
      //Wacc.zero;
      for ((fv, c) <- features) {
        val updw = (iterations * len - (len * i + (s+1)) + 1).asInstanceOf[Double]
        val oracleScore = scoreTarget(c, fv)
        val scores = sortScores(fv)
        // Setup b
        for (n <- 0 until km) {
          val targetP = classes(scores(n)._2) == c
          val loss = if (targetP) 0.0 else 1.0
          val dist = oracleScore - scores(n)._1
          b(n) = loss - dist
          distvec(n) = SLFeatureVector.getDistVector(fv.slvec(0x1000000 * classIndex(c)), fv.slvec(0x1000000 * scores(n)._2))
          // binary
          //distvec(n) = if (targetP) SLFeatureVector.getDistVector(fv.slvec, fv.slvec)
          //             else SLFeatureVector.getDistVector(FV.zero.slvec, fv.slvec);
        }
        // Solve for alphas
        val alpha = QPSolver.hildreth(distvec, b, C)
        if (false) { //true)
          println("-------------------------------------------")
          println("trial " + s + ", target: " + c)
          println("score: " + scores.map {case (score, idx) => (score -> classes(idx))}.mkString(" "))
          println("distv: " + distvec.mkString(" ::: "))
          println("b    : " + b.mkString(" "))
          println("alpha: " + alpha.mkString(" "))
        }
        val corr = classIndex(c)

        for (n <- 0 until km) {
          val model = scores(n)._2
          var d = distvec(n)
          while(d != null) {
            if (d.index >= 0) {
              // binary
              //weights(model)(d.index) += alpha(n) * d.value;
              // weights(corr)(d.index) -= alpha(n) * d.value;
              val m = (d.index >> 24)
              weights(m)(d.index & 0xffffff) += alpha(n) * d.value
              Wacc(m)(d.index & 0xffffff) += updw * alpha(n) * d.value
            }
            d = d.next
          }
        }
        if (false)
          for (cl <- 0 until weights.length)
            log("INFO", "New weights[" + classes(cl) + "]: " + weights(cl).filter(_ != 0.0).toList.sortWith(_ < _).toString)
        s += 1
        //Wacc += weights;
      }
    }
    if (average)  {
      log("INFO", "Online weights: " + weights(0).slice(0, 20).map { x => sprintf("%.4f", double2Double(x)) }.mkString(" "))
      Wacc /= iterations * len
      weights << Wacc
      log("INFO", "Online weights: " + weights(0).slice(0, 20).map { x => sprintf("%.4f", double2Double(x)) }.mkString(" "))
    }

    if (false) {
      // Print heavy hitters
      val dict = HashMap[Int, String]()
      val R = """^\s*(\d+)\s+(.*?)\s*$""".r
      val fname = "id_my_mturk_raw"
      for (l <- Source.fromInputStream(new FileInputStream("dicts/"+fname+".txt"), "UTF8").getLines)
        l.trim match { case R(idx, word) => dict(idx.toInt) = word; }
 //     for (c <- 0 until weights.length) {
 //       var outfile = "heavy_hitters/"+fname+"/"+classes(c)+".txt"
 //       val pw = new PrintWriter(new File(outfile))
 //       var outstring = ""
 //       val hp = weights(c).zipWithIndex.toList.sortWith(_._1 > _._1).slice(0, 20);
//       val hn = weights(c).zipWithIndex.toList.sortWith(_._1 < _._1).slice(0, 20);

//        println("""\begin{table}[htb]""");
//        println("""  \center""");
//        println("""  \begin{tabular}{|r|l||r|l|}""");
//        println("""  \hline""");
//        println("""  {\bf Most Indicative} & {\bf +} & {\bf Most Contra-indicative} & {\bf -} \\""");
//        println("""  \hline""");
//        for (i <- 0 until hp.length) {
//          System.out.printf("  %20s & %.3f &", dict(hp(i)._2), double2Double(hp(i)._1));
//          System.out.printf("%20s & %.3f \\\\\n", dict(hn(i)._2), double2Double(hn(i)._1));
//        }
//        println("""  \hline""");
//        println("""  \end{tabular}""");
//        println("""  \caption{Dominant features for Class """ + classes(c) + "}");
//        println("""  \label{tab:level-""" + classes(c) + "}");
//        println("""\end{table}""");


//        outstring = "Most Indicative - " + classes(c) + "\n"
//        for (i <- 0 until hp.length) {
//          outstring = outstring + dict(hp(i)._2) + " " + double2Double(hp(i)._1) + "\n"
//        }
//        outstring = outstring + "Most Contra-Indicative - " + classes(c) + "\n"
//        for (i <- 0 until hp.length) {
//          outstring = outstring + dict(hn(i)._2) + " " + double2Double(hn(i)._1) + "\n"
//        }
//        outstring = outstring + "\n\n"
//        pw.write(outstring)
//        pw.close
//      }
    }

  }
  override def regress(features : Iterable[(FV, Symbol)], average : Boolean, iterations : Int)(implicit log : Log) {
    val km = math.min(k, classes.length)
    var len = 0

    for ((fv, c) <- features) len += 1

    for (i <- 0 until iterations) {
      var s = 0
      var mse = 0.0
      if ((0<=i && i<=4) || ((iterations - 3)<=i && i<iterations)) log("INFO", "Starting training iteration: %d", int2Integer(i))
      for ((fv, c) <- features) {
        val updw = (iterations * len - (len * i + (s+1)) + 1).asInstanceOf[Double]
        val score = fv * weights(0); //fv.keys.foldLeft(0.0) { (sum, i) => sum + weights(0)(i) * fv(i) }; // score = w * x
        
        val loss = math.abs(c.name.toDouble - score); // y - w * x
        val margin = 0.0; // zero = perceptron
        val n = fv.normp(2.0)

        val alpha = (if (c.name.toDouble > score) 1.0 else -1.0) * math.max(0.0, math.min(C, if (n == 0.0) 0.0 else (loss - margin) / n))
        if (false) {//true)
          println("-------------------------------------------")
          println("trial " + s + ", target: " + c)
          println("score: " + score)
          println("alpha: " + alpha)
        }
        fv.iterate { (i, d) =>
          weights(0)(i) += alpha * d
          Wacc(0)(i) += updw * alpha * d;
        }

        if (false)
          for (cl <- 0 until weights.length)
            log("INFO", "New weights[" + classes(cl) + "]: " + weights(cl).filter(_ != 0.0).toList.sortWith(_ < _).toString)
        mse += loss * loss
        s += 1
      }
      if ((0<=i && i<=4) || ((iterations - 3)<=i && i<iterations)) log("INFO", "Mean Square Error: %f\n", double2Double(mse / s))
    }
    if (average) {
      log("INFO", "Online weights: " + weights(0).slice(0, 20).map { x => sprintf("%.4f", double2Double(x)) }.mkString(" "))
      Wacc /= iterations * len
      weights << Wacc
      log("INFO", "Online weights: " + weights(0).slice(0, 20).map { x => sprintf("%.4f", double2Double(x)) }.mkString(" "))
    }
  }
}

/*********************************************************************************************/
class Perceptron(w : Array[Array[Double]], c : Array[Symbol]) extends LinearModel(w, c) {
  var Wacc = weights.map { x => Array.fill(x.length)(0.0); }
  override def train(features : Iterable[(FV, Symbol)], average : Boolean, iterations : Int)(implicit log : Log) {
    var len = 0

    for ((fv, c) <- features) len += 1

    for (i <- 0 until iterations) {
      var s = 0
      log("INFO", "Starting training iteration: %d", int2Integer(i))
      for ((fv, c) <- features) {
        val updw = (iterations * len - (len * i + (s+1)) + 1).asInstanceOf[Double]
        val scores = sortScores(fv)
        val corr = classIndex(c)
        var cscore = -10000000.0
        //if (false) log("Example: " + c + ", feature vector: " + fv.toList.sort(_._1 < _._1).map(_._2));
        for ((s, c) <- scores) if (c == corr) cscore = s

        if (corr != scores(0)._2)
          for (m <- 0 until scores.length) {
            val model = scores(m)._2
            val sign = if (model == corr) 1.0
                       else -1.0 / (scores.length - 1.0)
            fv.iterate { (i, d) =>
              weights(model)(i) += sign * d
              Wacc(model)(i) += updw * sign * d;
            }
          }
        if (false)
          for (cl <- 0 until weights.length)
            log("INFO", "New weights[" + classes(cl) + "]: " + weights(cl).filter(_ != 0.0).toList.toString); //.sort(_ < _).toString);
        s += 1
      }
    }
    if (average) {
      log("INFO", "Online weights: " + weights(0).slice(0, 20).map { x => sprintf("%.4f", double2Double(x)) }.mkString(" "))
      Wacc /= iterations * len
      weights << Wacc
      log("INFO", "Online weights: " + weights(0).slice(0, 20).map { x => sprintf("%.4f", double2Double(x)) }.mkString(" "))
    }
  }
  override def regress(features : Iterable[(FV, Symbol)], average : Boolean, iterations : Int)(implicit log : Log) {
    var len = 0
    assert(false, "DON'T do this: not implemented fully")

    for ((fv, c) <- features) len += 1

    for (i <- 0 until iterations) {
      var s = 0
      var mse = 0.0
      log("INFO", "Starting training iteration: %d", int2Integer(i))
      for ((fv, c) <- features) {
        val updw = (iterations * len - (len * i + (s+1)) + 1).asInstanceOf[Double]
        val score = fv * weights(0); //fv.keys.foldLeft(0.0) { (sum, i) => sum + weights(0)(i) * fv(i) }; // score = w * x
        
        val loss = math.abs(c.name.toDouble - score); // y - w * x
        val margin = 0.0; // zero = perceptron
        val n = fv.norm(2.0)

        val alpha = (if (c.name.toDouble > score) 1.0 else -1.0) * loss
        if (false) {//true)
          println("-------------------------------------------")
          println("trial " + s + ", target: " + c)
          println("score: " + score)
          println("alpha: " + alpha)
        }
        fv.iterate { (i, d) => //for (m <- fv.keys) {
          weights(0)(i) += alpha * d
          Wacc(0)(i) += updw * alpha * d;
        }

        if (false)
          for (cl <- 0 until weights.length)
            log("INFO", "New weights[" + classes(cl) + "]: " + weights(cl).filter(_ != 0.0).toList.sortWith(_ < _).toString)
        mse += loss * loss
        s += 1
      }
      log("INFO", "Mean Square Error: %f\n", double2Double(mse / s))
    }
    if (average) {
      log("INFO", "Online weights: " + weights(0).slice(0, 20).map { x => sprintf("%.4f", double2Double(x)) }.mkString(" "))
      Wacc /= iterations * len
      weights << Wacc
      log("INFO", "Online weights: " + weights(0).slice(0, 20).map { x => sprintf("%.4f", double2Double(x)) }.mkString(" "))
    }
  }
}

/*********************************************************************************************/
class SVM(w : Array[Array[Double]], c : Array[Symbol], val C : Double, val gamma : Double) extends LinearModel(w, c) {
  var Wacc = weights.map { x => Array.fill(x.length)(0.0); }
  var model : svm_model = null

  def setup(regress : Boolean, features : Iterable[(FV, Symbol)]) : svm_problem = {
    val prob = new svm_problem()
    val truth = new ArrayBuffer[Double]()
    val vecs = new ArrayBuffer[Array[svm_node]]()

    for ((fv, c) <- features) {
      truth += (if (regress) c.name.toDouble else classIndex(c).toDouble)
      vecs += fv.svmnode
    }
    prob.l = truth.length
    prob.y = truth.toArray
    prob.x = vecs.toArray

    prob
  }
  def setupParameters(regress : Boolean, nu : Boolean) : svm_parameter = {
    val param = new svm_parameter()

    // default values
    if (nu) param.svm_type = if (regress) svm_parameter.NU_SVR else svm_parameter.NU_SVC
    else param.svm_type = if (regress) svm_parameter.EPSILON_SVR else svm_parameter.C_SVC
    param.kernel_type = svm_parameter.LINEAR
    param.degree = 3
    param.gamma = gamma; //1.0 / size.toDouble;	// 1/num_features
    param.coef0 = 0
    param.nu = 0.5
    param.cache_size = 100
    param.C = C
    param.eps = 1e-3
    param.p = 0.1
    param.shrinking = 1
    param.probability = 0
    param.nr_weight = 0
    param.weight_label = new Array[Int](0)
    param.weight = new Array[Double](0)

    param
  }
  def transfer(model : svm_model) { // convert to simple linear classifer
    val start = new ArrayBuffer[Int]()
    var p = 0

    start += 0
    for (i <- 1 until model.nr_class)
      start += start(i-1) + model.nSV(i-1)

    for (i <- 0 until model.nr_class)
      for (j <- (i + 1) until model.nr_class) {
        // sum support vectors
        for (off <- 0 until model.nSV(i)) {
          val sv = model.SV(start(i) + off)
          for (d <- 0 until sv.length) weights(i)(sv(d).index) += model.sv_coef(j-1)(start(i) + off) * sv(d).value
        }
        
        for (off <- 0 until model.nSV(j)) {
          val sv = model.SV(start(j) + off)
          for (d <- 0 until sv.length) weights(i)(sv(d).index) += model.sv_coef(i)(start(j) + off) * sv(d).value
        }
        p += 1
      }
  }
  override def train(features : Iterable[(FV, Symbol)], average : Boolean, iterations : Int)(implicit log : Log) {
    val problem = setup(false, features)
    val params = setupParameters(false, false)

    model = svm.svm_train(problem, params)
  }
  override def regress(features : Iterable[(FV, Symbol)], average : Boolean, iterations : Int)(implicit log : Log) {
    val problem = setup(true, features)
    val params = setupParameters(true, false)

    model = svm.svm_train(problem, params)
  }
  override def csortScores(fv : FV) : Array[(Double, Symbol)] = {
    val values = new Array[Double](model.nr_class * (model.nr_class - 1) / 2)
    val label = svm.svm_predict_values(model, fv.svmnode, values)
    Array[(Double, Symbol)](label -> classes(if (label >= classes.length || label <= 0) classes.length - 1 else label.toInt));   //BUG: doesn't break by giving a 'junk' value if out of bounds (i.e. <0 or >7) - solution fine for regression, would be inaccurate for classification
  }
}
/*********************************************************************************************/
class SVMOPEN(w : Array[Array[Double]], c : Array[Symbol], val C : Double, val gamma : Double) extends LinearModel(w, c) {
  var Wacc = weights.map { x => Array.fill(x.length)(0.0); }
  var model : svm_model = null

  def setup(regress : Boolean, features : Iterable[(FV, Symbol)]) : svm_problem = {
    val prob = new svm_problem()
    val truth = new ArrayBuffer[Double]()
    val vecs = new ArrayBuffer[Array[svm_node]]()

    for ((fv, c) <- features) {
      truth += (if (regress) c.name.toDouble else classIndex(c).toDouble)
      vecs += fv.svmnode
    }
    prob.l = truth.length
    prob.y = truth.toArray
    prob.x = vecs.toArray

    prob
  }
  def setupParameters(regress : Boolean, nu : Boolean) : svm_parameter = {
    val param = new svm_parameter()

    // default values
    if (nu) param.svm_type = if (regress) svm_parameter.NU_SVR else svm_parameter.NU_SVC
    else param.svm_type = if (regress) svm_parameter.EPSILON_SVR else svm_parameter.C_SVC
    param.kernel_type = svm_parameter.LINEAR
    param.degree = 3
    param.gamma = gamma; //1.0 / size.toDouble;	// 1/num_features
    param.coef0 = 0
    param.nu = 0.5
    param.cache_size = 100
    param.C = C
    param.eps = 1e-3
    param.p = 0.1
    param.shrinking = 1
    param.probability = 0
    param.nr_weight = 0
    param.weight_label = new Array[Int](0)
    param.weight = new Array[Double](0)

    param
  }
  def transfer(model : svm_model) { // convert to simple linear classifer
    val start = new ArrayBuffer[Int]()
    var p = 0

    start += 0
    for (i <- 1 until model.nr_class)
      start += start(i-1) + model.nSV(i-1)

    for (i <- 0 until model.nr_class)
      for (j <- (i + 1) until model.nr_class) {
        // sum support vectors
        for (off <- 0 until model.nSV(i)) {
          val sv = model.SV(start(i) + off)
          for (d <- 0 until sv.length) weights(i)(sv(d).index) += model.sv_coef(j-1)(start(i) + off) * sv(d).value
        }
        
        for (off <- 0 until model.nSV(j)) {
          val sv = model.SV(start(j) + off)
          for (d <- 0 until sv.length) weights(i)(sv(d).index) += model.sv_coef(i)(start(j) + off) * sv(d).value
        }
        p += 1
      }
  }
  override def train(features : Iterable[(FV, Symbol)], average : Boolean, iterations : Int)(implicit log : Log) {
    val problem = setup(false, features)
    val params = setupParameters(false, false)

    model = svm.svm_train(problem, params)
  }
  override def regress(features : Iterable[(FV, Symbol)], average : Boolean, iterations : Int)(implicit log : Log) {
    val problem = setup(true, features)
    val params = setupParameters(true, false)

    model = svm.svm_train(problem, params)
  }
  override def csortScores(fv : FV) : Array[(Double, Symbol)] = {
    val values = new Array[Double](model.nr_class * (model.nr_class - 1) / 2)
    val label = svm.svm_predict_values(model, fv.svmnode, values)
    Array[(Double, Symbol)](label -> classes(if (label >= classes.length || label <= 0) classes.length - 1 else label.toInt));   //BUG: doesn't break by giving a 'junk' value if out of bounds (i.e. <0 or >7) - solution fine for regression, would be inaccurate for classification
  }
}
