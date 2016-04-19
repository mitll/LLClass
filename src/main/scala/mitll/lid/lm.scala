/*
 *  Written by Wade Shen <swade@ll.mit.edu>
 *  Copyright 2005-2016 Massachusetts Institute of Technology, Lincoln Laboratory
 *  Revision: 0.2
 */
package mitll.lid
import scala.collection.mutable.{HashMap, ListBuffer, Map}
import scala.io.{BufferedSource, Source}

// -------------------------------------------------------------------------------------------------------------------------------------
// LM : Original version
// -------------------------------------------------------------------------------------------------------------------------------------
class LMOrig(fn : String) {
  def read(fn : String) : Array[Map[List[String], (Float, Float)]] = {
    var ret = List[Map[List[String], (Float, Float)]]()
    var current : Map[List[String], (Float, Float)] = null
    var order = 0

    val fromFile: BufferedSource = Source.fromFile(fn, "UTF8")
    for (line <- fromFile.getLines) {
      line.trim match {
        case orderline(o) => current = HashMap[List[String], (Float, Float)](); ret ++= List(current); order = o.toInt;
        case _ => if (order > 0) { 
          val lt = line.trim.split("""\s+""")
          if (lt.length == order + 1) current(lt.slice(1, lt.length).toList) = (lt(0).toFloat, 0.0f)
          else if (lt.length == order + 2) current(lt.slice(1, lt.length - 1).toList) = (lt(0).toFloat, lt(lt.length - 1).toFloat)
        }
      }
    }
    fromFile.close
    ret.toArray
  }

  def apply(sent : String) : Float = {
    var score = 0.0f
    val order = ngs.length
    var history = new ListBuffer[String](); history += "<s>"

    for (w <- (sent.trim.split("""\s+""") ++ List("</s>"))) {
      val ng = history.toList ++ List(w)
      val o = ng.length

      if (ngs(o-1) isDefinedAt ng) score += ngs(o-1)(ng)._1; //{ println("P: " + ng + " score: " + ngs(o-1)(ng)._1); score += ngs(o-1)(ng)._1; }
      else {
        var i = 1
        var done = false
        var x = 0.0f

        while(i < o && !done) {
          val bo = history.drop(i).toList ++ List(w);  // a [ b c ]
          val context = history.drop(i-1).toList; // [ a  b ] c
          
          if (ngs(o-i-1) isDefinedAt context) x += ngs(o-i-1)(context)._2
          if (ngs(o-i-1) isDefinedAt bo) { x += ngs(o-i-1)(bo)._1; done = true; }
          i += 1
        }
        if (done) score += x
        //println("BO: " + ng + " score: " + x);
        // else : nothing mimic srilm (-inf == 0)
      }

      if (history.length < order - 1) history += w
      else { history.remove(0); history += w; } //history = history.drop(1) + w;
    }
    score
  }

  val orderline = """^\\([0-9]+)-grams:$""".r
  val ngs : Array[Map[List[String], (Float, Float)]] = read(fn)
}

// -------------------------------------------------------------------------------------------------------------------------------------
// LM : array of ints
// -------------------------------------------------------------------------------------------------------------------------------------
class K(val d : Array[Int]) { 
  override def hashCode = {
    var i = 0
    var x = K.seed
    while(i < d.length)
    { x = (x ^ d(i)) * 0x1000193; i += 1; }
    x
  }
  override def equals(o : Any) : Boolean = o match {
    case o : K => 
      if (d.length != o.d.length) false
      else {
        var i = 0
        while(i < d.length) { if (d(i) != o.d(i)) return false; i+=1; }
        true
      }
    case _ => false;
  }
  override def toString = d.map(_.toString).mkString(" ")
}
object K { 
  val seed = 0x811C9DC5
  val offset = 2166136261l
  val prime = 16777619

  def apply(l : List[Int]) = new K(l.toArray); def apply(l : Array[Int]) = new K(l)
}

class LM(fn : String) {
  def read(fn : String) : Array[Map[K, Array[Float]]] = {
    var ret = new ListBuffer[Map[K, Array[Float]]]()
    val sizer = """^ngram (\d+)=(\d+)$""".r
    val emptyline = """^(\s*)$""".r
    val splitter = """\s+""".r
    var sizes = HashMap[Int, Int]()
    var current : Map[K, Array[Float]] = null
    var windex, order = 0
    val fromFile: BufferedSource = Source.fromFile(fn, "UTF8")
    val iter = fromFile.getLines
    var header = true

    // read header first
    iter.next; // skip blank
    while(header) {
      val line = iter.next
      line.trim match {
        case sizer(o, s) => sizes(o.toInt) = s.toInt;
        case emptyline(x) => header = false;
        case _ => header = true;
        }
    }
    vocab = new HashMap[String, Int]()

    for (line <- iter) {
      val lt = splitter.pattern.split(line.trim)
      lt(0) match {
        case orderline(o) => current = new HashMap[K, Array[Float]](); ret += current; order = o.toInt;
        case _ => 
          if (lt.length == order + 1) {
            if (order == 1) { vocab(lt(1)) = windex; windex += 1; }
            val k = lt.slice(1, lt.length).map(vocab(_)).toArray; //new Array[Int](lt.length-1); for (i <- 1 until lt.length) k(i-1) = vocab(lt(i));
            current(K(k)) = Array(lt(0).toFloat, 0.0f)
          }
          else if (lt.length == order + 2) {
            if (order == 1) { vocab(lt(1)) = windex; windex += 1; }
            val k = lt.slice(1, lt.length - 1).map(vocab(_)).toArray; // new Array[Int](lt.length-2); for (i <- 1 until lt.length - 1) k(i-1) = vocab(lt(i));
            current(K(k)) = Array(lt(0).toFloat, lt(lt.length - 1).toFloat)
          }
        }
      }
    fromFile.close
    ret.toArray
  }

  def apply(sent : String) : Float = {
    var score = 0.0f
    val order = ngs.length
    var history = new ListBuffer[Int](); history += vocab("<s>")

    for (w <- (sent.trim.split("""\s+""").map(vocab.getOrElse(_, -1)) ++ List(vocab("</s>")))) {
      val ng = K(history.toList ++ List(w))
      val o = history.length + 1

      if (ngs(o-1) isDefinedAt ng) score += ngs(o-1)(ng)(0); //{ println(" ++ P: " + ng + " score: " + ngs(o-1)(ng)(0)); score += ngs(o-1)(ng)(0); }
      else {
        var i = 1
        var done = false
        var x = 0.0f

        while(i < o && !done) {
          var bo = K(history.drop(i).toList ++ List(w)); // a [ b c ]
          val context = K(history.drop(i-1).toList); // [ a  b ] c
          
          if (ngs(o-i-1) isDefinedAt context) x += ngs(o-i-1)(context)(1)
          if (ngs(o-i-1) isDefinedAt bo) { x += ngs(o-i-1)(bo)(0); done = true; }
          //printf(" ++ bo = %s, context = %s --> %f\n", bo, context, x);
          i += 1
        }
        if (done) score += x
        //println(" ++ BO: " + ng + " score: " + x);
        // else : nothing mimic srilm (-inf == 0)
      }
      //printf(" ++ %s: %f\n", ng, score);

      if (history.length < order - 1) history += w
      else { history.remove(0); history += w; } //history = history.drop(1) + w;
    }
    score
  }

  val orderline = """^\\([0-9]+)-grams:$""".r
  var vocab : HashMap[String, Int] = null
  val ngs : Array[Map[K, Array[Float]]] = read(fn)
}

// -------------------------------------------------------------------------------------------------------------------------------------
// LM using string
// -------------------------------------------------------------------------------------------------------------------------------------
class LMS(fn : String) {
  def read(fn : String) : Array[Map[String, Array[Float]]] = {
    var ret = new ListBuffer[Map[String, Array[Float]]]()
    val sizer = """^ngram (\d+)=(\d+)$""".r
    val emptyline = """^(\s*)$""".r
    var sizes = HashMap[Int, Int]()
    var current : Map[String, Array[Float]] = null
    var order = 0
    val fromFile: BufferedSource = Source.fromFile(fn, "UTF8")
    val iter = fromFile.getLines
    var header = true

    // read header first
    iter.next; // skip blank
    while(header) {
      val line = iter.next
      line.trim match {
        case sizer(o, s) => sizes(o.toInt) = s.toInt;
        case emptyline(x) => header = false;
        case _ => header = true;
        }
    }

    for (line <- iter) {
      line.trim match {
        case orderline(o) => 
          current = new HashMap[String, Array[Float]]() { override def initialSize = sizes(o.toInt) * 2; threshold = sizes(o.toInt) }
          ret += current; order = o.toInt;
        case _ => 
          val lt = """\s+""".r.pattern.split(line.trim); //line.trim.split("""\s+"""); 
          if (lt.length == order + 1) current(lt.slice(1, lt.length).mkString(" ")) = Array(lt(0).toFloat, 0.0f)
          else if (lt.length == order + 2) current(lt.slice(1, lt.length - 1).mkString(" ")) = Array(lt(0).toFloat, lt(lt.length - 1).toFloat); 
      }
    }
    fromFile.close

    ret.toArray
  }

  def apply(sent : String) : Float = {
    var score = 0.0f
    val order = ngs.length
    var history = new ListBuffer[String](); history += "<s>"

    for (w <- (sent.trim.split("""\s+""") ++ List("</s>"))) {
      val ng = history.mkString(" ") + " " + w; //history.toList ++ List(w); 
      val o = history.length + 1

      if (ngs(o-1) isDefinedAt ng) { println(" ++ P: " + ng + " score: " + ngs(o-1)(ng)(0)); score += ngs(o-1)(ng)(0); }
      else {
        var i = 1
        var done = false
        var x = 0.0f

        while(i < o && !done) {
          var bo = history.drop(i).mkString(" "); bo = if (bo == "") w else bo + " " + w; // a [ b c ]
          val context = history.drop(i-1).mkString(" "); // [ a  b ] c
          
          if (ngs(o-i-1) isDefinedAt context) x += ngs(o-i-1)(context)(1)
          if (ngs(o-i-1) isDefinedAt bo) { x += ngs(o-i-1)(bo)(0); done = true; }
          printf(" ++ bo = %s, context = %s --> %f\n", bo, context, x)
          i += 1
        }
        if (done) score += x
        println(" ++ BO: " + ng + " score: " + x)
        // else : nothing mimic srilm (-inf == 0)
      }
      //printf(" ++ %s: %f\n", ng, score);

      if (history.length < order - 1) history += w
      else { history.remove(0); history += w; } //history = history.drop(1) + w;
    }
    score
  }

  val orderline = """^\\([0-9]+)-grams:$""".r
  val ngs : Array[Map[String, Array[Float]]] = read(fn)
}

object LMTest {
  def main(args : Array[String]) {
    // test lm
    val lm = new LM("tests/iwslt09-ae/data/class-7.srilm")
    val wc = new WC("tests/iwslt09-ae/data/lm-class")
    for (fn <- args; line <- Source.fromFile(fn, "UTF8").getLines) {
      val x = wc.translate(line.trim.split("""\s+"""))
      println("score: " + x._1 + " (score: " + lm(x._1) + " " + x._2 + " = " + (lm(x._1) + x._2) + ")")
    }
  }
}

// -------------------------------------------------------------------------------------------------------------------------------------
// WC : Word Class
// -------------------------------------------------------------------------------------------------------------------------------------
class WC(fn : String) {
  def read(fn : String) = {
    val fromFile: BufferedSource = Source.fromFile(fn, "UTF8")
    for (l <- fromFile.getLines) l match { 
      case matcher1(cl, p, w) => map(new String(w)) = (new String(cl), p.toFloat);
      case matcher2(w, cl) => map(new String(w)) = (new String(cl), 1.0f); 
    }
    fromFile.close
  }
  def log10(in : Float) : Float = if (in == 0.0f) 0.0f else (math.log(in) / math.log(10.0)).asInstanceOf[Float]

  def translate(sent : Array[String]) : (String, Float) = {
    var scr = 0.0f; (sent.map { w => val (c, p) = map.getOrElse(w, ("<unk>", 0.0f)); scr += log10(p); c }.mkString(" "), scr) }
  
  val matcher1 = """^(.*?)\s+(.*?)\s+(.*?)\s*$""".r; // ngram-class output format
  val matcher2 = """^(.*?)\s+(.*?)\s*$""".r; // mkcls output format
  val map = HashMap[String, (String, Float)]()
  read(fn)
}
