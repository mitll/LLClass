
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
import java.io._
import java.util.zip._
import javax.swing.filechooser.FileSystemView

import org.apache.commons.io.FileUtils._

import scala.collection.immutable.Vector
import scala.collection.mutable.{ArrayBuffer, HashMap, ListBuffer, Map}
import scala.collection.{Iterable, IterableView}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.io.{BufferedSource, Source}

object utilities {

  // -------------------------------------------------------------------------------------------------------------------------------------
  // utilities
  // -------------------------------------------------------------------------------------------------------------------------------------
  class Path(s : String) { def /(o : String) = if (s != "") s + File.separator + o else o; def /(o : Any) : String = this./(o.toString); def %(o : Any*) = s.format(o : _*); }
  implicit def s2f(s : String) = new File(s) { def existe = exists(); def fsize = length(); }
  implicit def s2path(s : String) = new Path(s)

  implicit def a2path(a : Arg[_]) = new Path(a.s)

  implicit def toS[T](a : Arg[T]) = a.toString; // Allow args to become strings
  def platform = 
    if (System.getProperty("os.name") matches """Mac OS X.*""") "macos"
    else if (System.getProperty("os.name") matches """Linux.*""") {
      if (System.getProperty("os.arch") == "i386") "linux"
      else if (System.getProperty("os.arch") == "x86_64" || System.getProperty("os.arch") == "amd64") "linux64"
      else "Unsupported-Linux-Architecture-" + System.getProperty("os.arch")
    }
    else if (System.getProperty("os.name") matches """Windows.*""") "win32"
    else "Unsupported-Operating-System-" + System.getProperty("os.name")
  def cp(src : String, tgt : String) = copyFile(src, tgt)

  def sprintf(text : String, xs : Any*) = text.format(xs : _*)

  val scpHeader = "#!/bin/sh\n" + "set -e\n\n"

  def writeItems[T](l : Seq[T], fn : String) = withPrint(fn) { ps => for (s <- l) ps.println(s); }
  def writeItems[T](l : Set[T], fn : String) = withPrint(fn) { ps => for (s <- l) ps.println(s); }
  def withPrint[T](fn : String, autoflush : Boolean = false, encoding : String = "UTF8")(block : (PrintStream) => T) : T = {
    val f = new PrintStream(if (fn matches """^.*\.gz$""") new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(fn)))
                            else new BufferedOutputStream(new FileOutputStream(fn)),
                            autoflush, encoding)
    try block(f) finally f.close
  }
  def withObjectInput[T](fn : String)(block : (ObjectInputStream) => T) = {
    val f = new ObjectInputStream(new FileInputStream(fn))
    try { block(f); } finally{ f.close; }
  }
  def withObjectOutput[T](fn : String)(block : (ObjectOutputStream) => T) = {
    val f = new ObjectOutputStream(new FileOutputStream(fn))
    try { block(f); } finally f.close
  }
  def runProcess(command : String, addargs : String*) : (Array[String], Int) = {
    val a = command.split("""\s+""") ++ addargs
    val p = new ProcessBuilder(a.toArray : _*)
    val proc = p.start()
    val errs = future { // prevent blocking on unread stderr
      val str = Source.fromInputStream(proc.getErrorStream())
      val ret = ArrayBuffer[String]()
      for (l <- str.getLines) ret += l
      str.close
      ret.toArray
    }
    val source = Source.fromInputStream(proc.getInputStream())
    val ret = ArrayBuffer[String]()
    for (line <- source.getLines) ret += line

    proc.waitFor()
    source.close
    (ret.toArray, proc.exitValue())
  }
  def unzip(zip : String, outdir : String) {
    val zf      = new ZipFile(zip)
    val entries = zf.entries
    while (entries.hasMoreElements) {
      val file = entries.nextElement.asInstanceOf[ZipEntry]
      val outf = outdir / file.getName()
      if (file.isDirectory) outf.mkdirs
      else {
        val buffer = new Array[Byte](1024)
        val is = new BufferedInputStream(zf.getInputStream(file))
        val dir = outf.getParentFile
        if (!dir.exists) dir.mkdirs
        val fos = new java.io.FileOutputStream(outf)
        while (is.available() > 0) {
          val count = is.read(buffer)
          if (count != -1) fos.write(buffer, 0, count)
        }
        fos.close()
        is.close()
      }
    }
  }
  def zip(output : String, dir : String) {
    def add(zip : ZipOutputStream, dorf : String) {
      val outname = dorf.replace("\\", "/").replaceFirst("^" + java.util.regex.Pattern.quote(dir) + """[/\\]*""", "")
      zip.putNextEntry(new ZipEntry(outname))
      if (!dorf.isDirectory) { 
        val in = new BufferedInputStream(new FileInputStream(dorf))
        val buffer = new Array[Byte](1024)
        var count = 0
        while (count != -1) {
          count = in.read(buffer)
          if (count != -1) zip.write(buffer, 0, count)
        }
        in.close
      }
      zip.closeEntry
    }
    val z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))
    for (f <- listFiles(dir, null, true).toArray) add(z, f.asInstanceOf[java.io.File].getPath)
    z.close
  }



  // -------------------------------------------------------------------------------------------------------------------------------------
  // org utilities
  // -------------------------------------------------------------------------------------------------------------------------------------
  val Headline      = """^(\*+)\s*(.*)$""".r
  val Orderedlist   = """^(\s*)(\d+)\.\s*(.*)$""".r
  val Unorderedlist = """^(\s*)([+-]+)\s*(.*)$""".r
  val Commandline   = """^(\$\s+.*)$""".r
  val Table         = """^\s*(\|.*\|)\s*$""".r
  val Tableline     = """^\s*(\|[+-]+\|)\s*$""".r
  val BlockStart    = """^\s*#\+BEGIN_(EXAMPLE|SRC|VERSE|CENTER|QUOTE)\s*$""".r
  val BlockEnd      = """^\s*#\+END_(EXAMPLE|SRC|VERSE|CENTER|QUOTE)\s*$""".r

  final val TEXT      = 0
  final val BLOCK     = 1
  final val COMMANDS  = 2
  var mode            = TEXT
  var curIn           = -1
  var depth           = -1

  def k2tag(kind : String, end : Boolean = false) : String =
    kind match {
      case "SRC"    => if (end) "</code></pre>" else "<pre><code>";
      case "CENTER" => if (end) "" else "p=.";
      case _        => if (end) "</pre>" else "<pre>";
    }
  def listHandler(indent : Int, pre : String, text : String) : String = {
    if (curIn == -1) depth = 1
    else if (curIn > indent) depth += 1
    else if (curIn < indent) depth -= 1
    curIn = indent
    val prefix = if (pre.matches("""\d+""")) "#" else "*"
    (prefix * depth) + " " + text
  }
  def listReset : String = {
    val ret = if (depth > -1) "\n" else ""
    curIn = -1; depth = -1
    ret
  }
  def o2t(l : String) : String =
    mode match {
      case COMMANDS => l match {
        case Commandline(cmd) => cmd;
        case _                => mode = TEXT; "</pre>\n" + l;
      }
      case BLOCK => l match {
        case BlockEnd(kind) => mode = TEXT; k2tag(kind, end = true);
        case _              => l;
      }
      case TEXT => l match {
        case Headline(lev, text)              => listReset + "h" + lev.length + ". " + text + "\n"; // \n needed
        case Orderedlist(indent, num, text)   => listHandler(indent.length, num, text);
        case Unorderedlist(indent, num, text) => listHandler(indent.length, num, text);
        case Commandline(cmd)                 => mode = COMMANDS; listReset + "<pre>\n" + cmd;
        case Tableline(row)                   => val a = listReset; if (a != "") a else null;
        case Table(row)                       => listReset + row;
        case BlockStart(kind)                 => mode = BLOCK; listReset + k2tag(kind);
        case _                                => listReset + l;
      }
    }
  def org2textile(fn : String) : Iterable[String] = FileLines(fn, maker = o2t).view.filter(_ != null)


  // -------------------------------------------------------------------------------------------------------------------------------------
  // API utilities
  // -------------------------------------------------------------------------------------------------------------------------------------
  val dFormat   = new java.text.SimpleDateFormat("MMddyyyy-HHmmss.SSS")

  def timeStamp = dFormat.format(new java.util.Date)

  def homeDir = FileSystemView.getFileSystemView.getHomeDirectory.getAbsolutePath

  val ltr = java.awt.ComponentOrientation.LEFT_TO_RIGHT
  val rtl = java.awt.ComponentOrientation.RIGHT_TO_LEFT


  // -------------------------------------------------------------------------------------------------------------------------------------
  // mira.classifier utilities
  // -------------------------------------------------------------------------------------------------------------------------------------
  def mergeVectors(dimA : Int) = { (ab : Tuple2[FV, FV]) => val (a, b) = ab; new FV((a.index zip a.data) ++ (b.index.map(_ + dimA) zip b.data)) }
  def counts2fv(dictionary : Map[Symbol, Int], unk : Boolean) = (counts : Seq[(Symbol, Int)]) => FV(counts, dictionary, unk)

  def norm(bkg : FV, cutoff : Double) = (vector : FV) => { vector.norm(vector.total); vector.tfllr(bkg, cutoff); vector }
  def lognorm(bkg : FV, cutoff : Double) = (vector : FV) => { vector.norm(vector.total); vector.tflog(bkg, cutoff); vector }
  def znorm(mean : FV, std : FV) = (vector : FV) => { vector.znorm(mean, std); vector }
  def splitfv[T](parts : Int = 1) = (fvs : Iterable[T]) => {
    val len = fvs.toSeq.length
    val size = math.round(len.toDouble / parts.toDouble)
    Vector(fvs.grouped(size.toInt).toSeq : _*)
  }
  def train(dim : Int, lmFactory : (Array[Array[Double]], Array[Symbol]) => LinearModel, average : Boolean, iterations : Int, regress : Boolean = false)(implicit log : Log) = {
    (input : Iterable[(FV, Symbol)]) =>
    log("INFO", "Starting training")
      val labelArr = input.map(_._2).toSeq
      val labels   = Set(labelArr : _*).toArray

      log("INFO", "Labels in the training data are: " + labels.map(_.name).mkString(" "))
      val model = lmFactory(labels.map(x => Array.fill(dim)(0.0)), labels)
      if (regress) model.regress(input, average, iterations)
    else model.train(input, average, iterations)
      log("INFO", "Completed training")

      model -> input;
  }

  def bkg(mincount : Int = 1, prune : Double = 0.0, unk : Boolean = true)(implicit log : Log) = (m : Any, vectors : Iterable[Map[Symbol, Int]]) => {
    val d = Map[Symbol, Int]()
    for (vec <- vectors) { 
      for ((k, v) <- vec) {
        if (!d.isDefinedAt(k)) d(k) = 0
        d(k) += v
      }
    }
    var unkCount = 0
    val bkgc = Map[Symbol, Int]()
    for ((k, v) <- d) if (v >= mincount) bkgc(k) = v; else unkCount += 1
    if (unk) bkgc(unkToken) = unkCount
    val index = bkgc.keys.toArray.sortWith(_.name < _.name)
    val dictionary = HashMap(index.zipWithIndex : _*)

    val bkg = FV(bkgc, dictionary, false)
    log("INFO", "bkg has %d features", bkg.data.length)
    bkg.norm(bkg.total, prune)
    (dictionary, index, bkg) -> vectors
  }
  def bkgS(mincount : Int = 1, prune : Double = 0.0, unk : Boolean = true)(implicit log : Log) = (m : Any, vectors : Iterable[Array[Symbol]]) => {
    val d = Map[Symbol, Int]() withDefaultValue 0
    for (vec <- vectors; k <- vec) d(k) += 1
    var unkCount = 0
    val bkgc = Map[Symbol, Int]()
    for ((k, v) <- d) if (v >= mincount) bkgc(k) = v; else unkCount += 1
    if (unk) bkgc(unkToken) = unkCount

    val index = bkgc.keys.toArray.sortWith(_.name < _.name)
    val dictionary = HashMap(index.zipWithIndex : _*)

    val bkg = FV(bkgc, dictionary, false)
    log("INFO", "bkg has %d features", bkg.data.length)
    bkg.norm(bkg.total, prune)
    //for ((i, d) <- (bkg.index zip bkg.data).sortWith(_._2 > _._2)) log("DEBUG", "bkg %20s %10.7f", index(i), d);
    (dictionary, index, bkg) -> vectors
  }
  def zbkg(implicit log : Log) = (m : Any, vectors : Iterable[FV]) => {
    var acc  = Map[Int, Double]() withDefaultValue 0.0
    var acc2 = Map[Int, Double]() withDefaultValue 0.0
    var tot  = 0.0
    for (vec <- vectors) {
      for ((k, i) <- (vec.data zip vec.index)) {
        acc(i) += k
        acc2(i) += k * k
      }
      tot += 1
    }

    log("INFO", "bkg has %d features", acc.size)
    acc.keys.map(k => acc(k) /= tot.toFloat)
    val mean = new FV(acc.toSeq)
    val std  = new FV((for (k <- acc.keys) yield k -> math.sqrt((acc2(k) / tot.toFloat) - (acc(k) * acc(k)))).toSeq)
    //    println((for (k <- acc.keys) yield k -> math.sqrt((acc2(k) / tot.toFloat) - (acc(k) * acc(k)))).toSeq);
    log("STDDEV " + (for (k <- acc.keys) yield k -> math.sqrt((acc2(k) / tot.toFloat) - (acc(k) * acc(k)))).toSeq)
    (mean, std) -> vectors
  }


/*
  def morfessor(mincount : Int = 1, prune : Double = 0.0, unk : Boolean = true)(implicit log : Log) = (m : Any, vectors : Iterable[Array[Symbol]]) => {
    val d = Map[Symbol, Int]() withDefaultValue 0;
    for (vec <- vectors; k <- vec) d(k) += 1;
    var unkCount = 0;
    val bkgc = Map[String, List[String]]();

    val listfile = java.io.File.createTempFile("doctmp",".gz","ALLseg/TMPdir"); //listfile.deleteOnExit;
    val listfilepath = listfile.getAbsolutePath();
    withPrint(listfilepath){f => for ((k,v) <- d) f.println(v + " " + k.name)};
    val segfile = java.io.File.createTempFile("segtmp",".gz","ALLseg/TMPdir"); //segfile.deleteOnExit;
    val segfilepath = segfile.getAbsolutePath();

    val returner = runProcess("make -B GZINPUT=" + listfilepath + " GZOUTPUT=" + segfilepath);
    val lines = ArrayBuffer[String]();
    if (returner._2.toInt == 0) {
      for (line <- FileLines(segfilepath)) {
        val s = line.replaceAll(" \\+ "," ");
        val allsegs = s.split(" ").toList;
        val freq = allsegs.head.toInt;
        val words = allsegs.tail;
        if (!words.isEmpty) {
          val thekey = words.head;
          val theseg = words.tail;
          if (freq >= mincount) bkgc(thekey) = theseg;   // no alternative here - if freq exists, it will be >=1
        }
        if (unk) bkgc(unkToken.name) = List("Whoops"); //UNKNOWN TOKEN -- need to have a default here that makes sense
      }
    }

    val index = bkgc.keys.toArray.sortWith(_ < _);
    val dictionary = HashMap(index.zipWithIndex : _*);
    (bkgc, dictionary, index) -> vectors
  }
*/

  def morfFS(mincount : Int = 1, prune : Double = 0.0, unk : Boolean = true, lang: String, pplthresh : Int)(implicit log : Log) = (m : Any, vectors : Iterable[Array[Symbol]]) => {
    val d = Map[Symbol, Int]() withDefaultValue 0
    for (vec <- vectors; k <- vec) d(k) += 1
    var unkCount = 0

    val bkgc = Map[String, List[String]](); //withDefaultValue List("Whoops not found");        { override def default(key:String) = List("Whoops not found") };
    
    val segfile = new java.io.File("pplsegs/" + lang + pplthresh + ".gz")
    val segfilepath = segfile.getAbsolutePath()

    val lines = ArrayBuffer[String]()
    for (line <- FileLines(segfilepath)) {
      val s = line.replaceAll(" \\+ "," ")
      val allsegs = s.split(" ").toList
      val freq = allsegs.head.toInt
      val words = allsegs.tail
      if (!words.isEmpty) {
        val thekey = words.head
        val theseg = words.tail
        if (freq >= mincount) bkgc(thekey) = theseg;   // no alternative here - if freq exists, it will be >=1
      }
      if (unk) bkgc(unkToken.name) = List("oops");     //UNKNOWN TOKEN -- need to have a default here that makes sense
    }

    val index = bkgc.keys.toArray.sortWith(_ < _)
    val dictionary = HashMap(index.zipWithIndex : _*)
    (bkgc, dictionary, index) -> vectors
  } 




  // -------------------------------------------------------------------------------------------------------------------------------------
  // Classify and friends
  // -------------------------------------------------------------------------------------------------------------------------------------
  def classify(input : Iterable[FV], model : LinearModel) = input map model.csortScores _

  def scoreClassification(scores : Iterable[(Array[(Double, Symbol)], Symbol)]): (Float, Array[String]) = {
    var (correct, total) = (0.0f, 0.0f)
    var confmat          = HashMap[Symbol, Map[Symbol, Double]]()
    def printConfMat: Array[String] = {
      val outputs = Set(confmat.values.flatMap(_.keySet).toSeq : _*).toList.sortWith(_.name > _.name)
      val truths  = confmat.keys.toList.sortWith(_.name > _.name)
      var ret = ArrayBuffer((Array("") ++ outputs.map(_.name) ++ Array("N", "class %")).map("%10s" % _).mkString(" "))
      for (t <- truths) {
        val rowTotal = confmat(t).foldLeft(0.0)((res, v) => res + v._2)
        ret += (Array("%10s" % t.name) ++ outputs.map(l => "%10d" % math.round(confmat(t)(l))) ++ 
                Array("%10d %10.6f" % (rowTotal.toInt, confmat(t)(t) / rowTotal))).mkString(" ")
      }
      ret.toArray
    }
    
    for ((score, truth) <- scores) {
      if (!confmat.isDefinedAt(truth)) confmat(truth) = HashMap[Symbol, Double]() withDefaultValue 0; // { override def default(key : Symbol) = 0; };
      val hl = score(0)._2
      confmat(truth)(hl) += 1.0
      if (truth == hl) correct += 1.0f
      total += 1.0f
    }
    (correct / total, printConfMat)
  }

  def scoreRegression(scores : Iterable[(Array[(Double, Symbol)], Symbol)]) = {
    var (mse, total) = (0.0, 0.0)
    var confmat      = HashMap[Symbol, Double]() withDefaultValue 0.0
    var nmat         = HashMap[Symbol, Double]() withDefaultValue 0.0

    for ((score, truth) <- scores) {
      val t = score(0)._1
      mse += (t - truth.name.toDouble) * (t - truth.name.toDouble)

      confmat(truth) += (t - truth.name.toDouble) * (t - truth.name.toDouble)
      nmat(truth) += 1.0
      total += 1.0
    }

    def printConfMat = (for ((k, v) <- confmat.toList.sortWith(_._1.name.toFloat < _._1.name.toFloat)) yield "%10s : %10.7f" % (k.name, confmat(k) / nmat(k))).toArray
    (mse / total, printConfMat)
  }




  // -------------------------------------------------------------------------------------------------------------------------------------
  // mira utilities
  // -------------------------------------------------------------------------------------------------------------------------------------
  implicit def toWV(w : Array[Array[Double]]) : WV = new WV(w)

  implicit def toAAD(w : WV) : Array[Array[Double]] = w.wv

  val unkToken = Symbol("--unk--")
  //implicit def sym2str(s : Symbol) = s.name;




  // -------------------------------------------------------------------------------------------------------------------------------------
  // text utilities
  // -------------------------------------------------------------------------------------------------------------------------------------
  // Konstants
  val defaultSpace = """(\s|\p{Zs})+"""

  // Simple tokenizers
  def wordTokenizer(space : String = defaultSpace) = (s : String) => s.trim.split(space).toSeq

  def charTokenizer = (s : String) => s.trim.split("").drop(1).toSeq

  def ideographTokenizer = (in : String) => in.trim.split("""\s+""").flatMap { w => if (w matches """[A-Za-z0-9]+""") Array(w) else w.split("").drop(1) }

  // Simple string manipulators
  def cleanSpace(space : String = defaultSpace) = (s : String) => s.trim.replaceAll(space, " ")

  def regexReplace(regex : String, replacement : String) = {
    val rg = regex.r.pattern
    (s : String) => rg.matcher(s).replaceAll(replacement);
  }
  val lowerCase = (s : String) => s.toLowerCase

  def decontract(map : Map[String, String] = Map("'s" -> "is", "'ll" -> "will", "n't" -> "not", "'re" -> "are", "'d" -> "would")) =
    (s : String) => """('s|'ll|n't|'re|'d)\b""".r.replaceAllIn(s, m => " " + (if (map isDefinedAt m.toString) map(m.toString) else m.toString))

  def normalizeURLs(nURL : String = "+URL+") = (s : String) => s.replaceAll("""(https?|ssh|ftp)|://\S+""", nURL)

  def normalizeCurrency(nCurrency : String = "+CURRENCY+") = (s : String) => s.replaceAll("""\p{Sc}\d+(,\d+)*\.?(\d+)?""", nCurrency)

  def normalizeDate(nDate : String = "+DATE+") = (s : String) => s.replaceAll("""\b\d+/\d+/\d+\b""", nDate)

  def normalizeTime(nTime : String = "+TIME+") = (s : String) => s.replaceAll("""\b\d+:\d+(:\d+)?\b""", nTime)

  def normalizeNumbers(nNumber : String = "+NUMBER+") = (s : String) => s.replaceAll("""\b\d+(\,\d+)*(\.\d+)*(s|th|%|st|nd|rd)?\b""", nNumber)

  val normalizeAbbrs = (s : String) => s.replaceAll("""([A-Z]\.)\s*([A-Z]\.)""", "$1$2")
  val normalizePunct = (s : String) => 
    s.replaceAll("""[\u2026\u22ee\ufe19]""", "...").replaceAll("""\p{Pd}""", "-").replaceAll("""(\p{Pi}|\p{Pf})""", "\"").replaceAll("""\p{Ps}""", "(").replaceAll("""\p{Pe}""", ")")

  // merge tokens from all lines into one collecton
  def tokensFromLines[T](tokenizer : String => Array[T]) = (it : Seq[String]) => (it flatMap(l => tokenizer(l.trim))).toSeq

  // return an Iterable[T] one T per line
  def tokensPerLine[T](tokenizer : String => T) = (it : Seq[String]) => for (l <- it) yield tokenizer(l.trim)

  // Count ngrams
  def ngrams(minOrder : Int = 1, maxOrder : Int = 3, separator : String = "-") = (s : Seq[String]) => {
    var ret = ArrayBuffer[Symbol]()
    for (o <- minOrder to maxOrder) {
      var history = ListBuffer[String]()
      for (c <- s) {
        if (history.length >= o) history.remove(0)
        history += c
        if (history.length == o) ret += Symbol(history.mkString(separator))
      }
    }
    ret.toArray
  }
  def ngrams2(minOrder : Int = 1, maxOrder : Int = 3, separator : String = "-") = (s : Seq[String]) => {
    var ret = ArrayBuffer[Symbol]()
    for (o <- minOrder to maxOrder; st <- 0 until (s.length - o + 1)) {
      val end = st + o
      ret += Symbol(s.slice(st, end).mkString(separator))
    }
    ret.toArray
  }

  // Use the Map method which is faster but more memory usage
  def countTokens(tokens : Array[Symbol]) : Map[Symbol, Int] = {
    val counts = HashMap[Symbol, Int]() withDefaultValue 0
    for (s <- tokens) counts(s) += 1
    counts
  }

  // Use the sparse vector method: slower, but less memory
  def countTokens2(tokens : Array[Symbol]) = {
    var curSym   = unkToken
    var curCount = 0
    val counts   = ArrayBuffer[(Symbol, Int)]()
    for (s <- tokens.sortWith(_.name < _.name))
      if (curSym != s) {
        if (curSym != unkToken) counts += curSym -> curCount
        curSym = s
        curCount = 1
      }
      else curCount += 1
    if (curCount > 0) counts += curSym -> curCount
    if (counts.length == 0) throw new Exception("Empty vector")
    counts
  }
  def AP5 = new AP5



  // -------------------------------------------------------------------------------------------------------------------------------------
  // File/Stream Readers
  // -------------------------------------------------------------------------------------------------------------------------------------
  class EagerStreamLines[T](fs : InputStream, enc : String = "UTF8", maker : (String) => T = (x : String) => x) extends Iterable[T] {
    //assert(fs.markSupported, "ERROR: inputstream to StreamLines doesn't support reset!");
    //fs.mark(size);
    def iterator = new Iterator[T] { 
      //fs.reset; 
      val bufferedSource: BufferedSource = Source.fromInputStream(fs, enc)
      val lines = bufferedSource.getLines
      def next = {
        val ret = maker(lines.next)
        if (!hasNext)
          bufferedSource.close
        ret
      }
      def hasNext = lines.hasNext
    } 
  }
  case class StreamLines[T](fs : InputStream, enc : String = "UTF8", maker : (String) => T = (x : String) => x) extends IterableView[T, EagerStreamLines[T]] {
    val a = new EagerStreamLines[T](fs, enc, maker)
    protected lazy val underlying = a

    override def iterator = a.iterator
  }
  class EagerFileLines[T](fn : String, enc : String = "UTF8", maker : (String) => T = (x : String) => x) extends Iterable[T] {
    //  def cachedBufferedSource = Source.fromInputStream(if (fn matches "^.*\\.gz$") new GZIPInputStream(new FileInputStream(fn)) else new FileInputStream(fn), enc);
    //  def iterator = cachedBufferedSource.getLines.map(maker);
    // NOTE: this no longer works in 2.9.1 -- they seem to have changed bufferedsource 
    private var cachedBufferedSource : BufferedSource = _
    def iterator = {
      if (cachedBufferedSource != null) cachedBufferedSource.close
      new Iterator[T] {
        val bufferedSource = Source.fromInputStream(if (fn matches "^.*\\.gz$") new GZIPInputStream(new FileInputStream(fn)) else new FileInputStream(fn), enc)
        
        cachedBufferedSource = bufferedSource
        val lines = bufferedSource.getLines
        
        def next = {
          val ret = maker(lines.next)
          //if (!hasNext) bufferedSource.close;
          ret
        }
        //def hasNext = lines.hasNext;
        def hasNext : Boolean = {
          val hn = lines.hasNext
          if (!hn) bufferedSource.close
          hn
        }
      }
    }
    def close = cachedBufferedSource.close
  }
  case class FileLines[T](fn : String, enc : String = "UTF8", maker : (String) => T = (x : String) => x) extends IterableView[T, EagerFileLines[T]] {
    val a = new EagerFileLines[T](fn, enc, maker)
    protected lazy val underlying = a

    override def iterator = a.iterator

    def close = underlying.close
  }


}
