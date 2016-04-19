/*
 *  Written by Wade Shen <swade@ll.mit.edu>
 *  Copyright 2005-2016 Massachusetts Institute of Technology, Lincoln Laboratory
 *  Revision: 0.2
 */
package mitll.lid
import java.io.{ObjectInputStream, ObjectOutputStream, PrintStream}

import libsvm._
import mitll.lid.utilities._
import struct.types.SLFeatureVector

import scala.collection.mutable.{ArrayBuffer, Map}

class FV(indexer : Seq[(Int, Double)]) extends Serializable {
  val (index, data) = {
    // sort both arrays
    val tmp = indexer.sortWith(_._1 < _._1)
    tmp.map(_._1).toArray -> tmp.map(_._2).toArray
  }
  
  def mkString(sep : String) : String = index.zipWithIndex.map { case (x, i) => "%d:%.2f" % (x, data(i)) }.mkString(sep)

  def mkString : String = mkString(" ")

  def mkString(start : String, sep : String, end : String) : String = start + mkString(sep) + end

  // API
  def writeKV(fn : String, wordIndex : Array[Symbol]) {
    withPrint(fn) { f => for ((c, i) <- index.zipWithIndex) f.println("\"%s\" %f" % (wordIndex(c).name, data(i))); } 
  }
  def writeLight(f : PrintStream) { f.println(index.zip(data).map { case (x, y) => x + ":" + y }.mkString(" ")); }
  def writeObj(f : ObjectOutputStream) { f.writeObject(this); }

  @inline final def *(o : FV) = {
    var c, d  = 0
    var res   = 0.0

    while(c < index.length) {
      while(o.index(d) < index(c) && d < o.index.length - 1) d += 1
      if (d <= o.index.length) res += data(c) * o.data(d)
      c += 1
    }
    res
  }
  @inline final def *(k : Double) = new FV(ArrayBuffer[(Int, Double)](index zip data.map(_ * k) : _*))

  @inline final def *(w : Array[Double]) = {
    var i = 0
    var res = 0.0
    while(i < index.length) {
      res += data(i) * w(index(i))
      i += 1
    }
    res
  }
  def tfnorm(denom : FV, limit : Double, squash : Double => Double) {
    var c, d  = 0
    while(c < index.length) {
//      println(c)
//      println(denom.index(d))
//      println(index(c))
//      println(d)
      while(denom.index(d) < index(c) && d < denom.index.length - 1) d += 1
      if (d <= denom.index.length) {
        val r = math.min(limit, squash(1.0 / denom.data(d)))
        //        println(r)
        data(c) *= r
        //        println(data(c))
      }
      c += 1
    }
  }
  def znorm(mean : FV, std : FV) {
    var c, d, e = 0
    while(c < index.length) {
      while(mean.index(d) < index(c) && d < mean.index.length - 1) d += 1
      while(std.index(e) < index(c) && e < std.index.length - 1) e += 1
      if (d <= mean.index.length && e <= std.index.length) data(c) = (data(c) - mean.data(d)) / std.data(d)
      c += 1
    }
  }
  def tfllr(denom : FV, limit : Double) { tfnorm(denom, limit, math.sqrt) }
  def tflog(denom : FV, limit : Double) { tfnorm(denom, limit, math.log) }
  def norm(denom : Double) { for (c <- 0 until index.length) data(c) /= denom; }
  def norm(denom : Double, thres : Double) { for (c <- 0 until index.length) { data(c) /= denom; if (data(c) < thres) data(c) = 0.0f } }
  def normp(p : Double) = math.pow(this * this, p / 2.0); // take the 2-norm, raise to the p power
  def total = data.foldLeft(0.0)(_ + _)

  // utilities
  def copy    = new FV(ArrayBuffer(index zip data : _*))

  def svmnode = ArrayBuffer(index.zip(data) : _*).map { case (k, v) => new svm_node { index = k; value = v; } }.toArray

  def slvec(offset : Int = 0) = {
    var result = new SLFeatureVector(-1, -1.0, null)
    for ((k, v) <- index zip data) result = new SLFeatureVector(k + offset, v, result)
    result
  }
  @inline final def iterate(f : (Int, Double) => Unit) {
    var i = 0
    while(i < index.length) {
      f(index(i), data(i))
      i += 1
    }
  }
}

object FV {
  val Reader      = """^\"(.*?)\"\s*(\S+)\s*$""".r
  val LightReader = """^(\d+):(.*)$""".r

  def featureSet(fn : String) = Set[Symbol]() ++ (for (Reader(ng, count) <- FileLines(fn)) yield Symbol(ng))

  def tokens2FV(counts : Map[Symbol, Int], dictionary : Map[Symbol, Int]) = {
    val indexer = ArrayBuffer[(Int, Double)]()
    for ((k, v) <- counts) indexer += dictionary(if (dictionary isDefinedAt k) k else unkToken) -> v.toDouble
    indexer
  }
  def tokens2FVDropUnk(counts : Map[Symbol, Int], dictionary : Map[Symbol, Int]) = {
    val indexer = ArrayBuffer[(Int, Double)]()
    for ((k, v) <- counts) if (dictionary isDefinedAt k) indexer += dictionary(k) -> v.toDouble
    indexer
  }
  def tokenSeq2FV(counts : Seq[(Symbol, Int)], dictionary : Map[Symbol, Int]) = 
    counts.map(w => dictionary.getOrElse(w._1, dictionary(unkToken)) -> w._2.toDouble)

  def tokenSeq2FVDropUnk(counts : Seq[(Symbol, Int)], dictionary : Map[Symbol, Int]) =
    counts.filter(dictionary isDefinedAt _._1).map(w => dictionary(w._1) -> w._2.toDouble)

  def readKV(fn : String, dictionary : Map[Symbol, Int]) = {
    val indexer = ArrayBuffer[(Int, Double)]()
    for ((l, no) <- FileLines(fn).zipWithIndex) {
      l.trim match {
        case Reader(ng, count) => 
          val idx = dictionary(Symbol(ng))
          indexer += idx -> count.toDouble;
        case e @ _ => throw Fatal("FV read error (line: " + no + "): " + e);
      }
    }
    indexer
  }
  def readLight(line : String) = {
    val indexer = ArrayBuffer[(Int, Double)]()
    for (kv <- line.trim.split("""\s+""")) {
      kv match {
        case LightReader(idx, value) => indexer += idx.toInt -> value.toDouble;
        case e @ _ => throw Fatal("FV read error: " + e);
      }
    }
    indexer
  }
  def zeroF = ArrayBuffer[(Int, Double)]()

  def kF(k : Double) = ArrayBuffer[(Int, Double)](); // TODO
  def apply(line : String) = new FV(readLight(line))

  def apply(counts : Seq[(Symbol,  Int)], dictionary : Map[Symbol, Int], unk : Boolean) = new FV(if (unk) tokenSeq2FV(counts, dictionary) else tokenSeq2FVDropUnk(counts, dictionary))

  def apply(fn : String, dictionary : Map[Symbol, Int]) = new FV(readKV(fn, dictionary))

  def apply(counts : Map[Symbol, Int], dictionary : Map[Symbol, Int], unk : Boolean) = new FV(if (unk) tokens2FV(counts, dictionary) else tokens2FVDropUnk(counts, dictionary))

  def obj(fn : String) = withObjectInput(fn) { f => f.readObject().asInstanceOf[FV] }
  def obj(f : ObjectInputStream) = f.readObject().asInstanceOf[FV]

  def zero = new FV(zeroF)
}

