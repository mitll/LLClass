/*
 *  Written by Wade Shen <swade@ll.mit.edu>
 *  Copyright 2005-2016 Massachusetts Institute of Technology, Lincoln Laboratory
 *  Revision: 0.2
 */
package mitll.lid
import mitll.lid.utilities._
import org.jtr.transliterate._

import scala.collection.mutable.{ArrayBuffer, HashMap, Map}
import scala.util.matching.Regex


// -------------------------------------------------------------------------------------------------------------------------------------
// Unsupervised Sentence Segmenter
// -------------------------------------------------------------------------------------------------------------------------------------
case class Token(word : String, endpos : Int, kind : Symbol = 'Word); // { def word = sym.name; }
//object Token { def apply(word : String, endpos : Int, kind : Symbol = 'Word) = new Token(Symbol(word), endpos, kind); }
class SentenceSegmenter(val boundary : String = SentenceSegmenter.SentenceBoundary, val tokenizer : String => Array[Token] = SentenceSegmenter.segToken _, 
                        val abbrThreshold : Float = 0.3f, val colThreshold : Float = 7.88f, val ssThreshold : Float = 30.0f) extends Serializable {


  val boundaryToken = Token("<s>", -1, 'Boundary)
  val pAbbr = 0.99f
  val B     = ("^" + boundary + "$").r
  var cA    = Map[String, Int]()
  var cAB   = Map[String, Int]()
  var sA    = Map[String, Int]()
  var sAB   = Map[String, Int]()
  var cPN   = Map[(String, String), Int]()
  var N     = 0
  var cB, sB= 0

  def flog(f : Double) = if (f < 1e-20f) -5000.0f else math.log(f)

  def binomLLR(cAbbr : Float, cWord : Float) = {
    val pB = cB / N.toFloat
    val nH = cAbbr * flog(pB) + (cWord - cAbbr) * flog(1.0f - pB)
    val aH = cAbbr * flog(pAbbr) + (cWord - cAbbr) * flog(1.0f - pAbbr)
    2.0f * (aH - nH)
  }
  def sStart(cStart : Float, cWord : Float, cB : Float) = {
    val p0 = cWord / N.toFloat
    val p  = cStart / cB
    val np = (cWord - cStart) / (N.toFloat - cB)

    val nH1 = cStart * flog(p0) + (cB - cStart) * flog(1.0 - p0)
    val nH2 = (cWord - cStart) * flog(p0) + (N.toFloat - cB - cWord + cStart) * flog(1.0 - p0)
    val aH1 = if (cB == cStart) 0.0f
              else cStart * flog(p) + (cB - cStart) * flog(1.0 - p)
    val aH2 = if (cWord == cStart) 0.0f
              else (cWord - cStart) * flog(np) + (N.toFloat - cB - cWord + cStart) * flog(1.0 - np)
    //println("ah1 = %f, ah2 = %f, nh1 = %f, nh2 = %f, SCORE = %f" format(aH1, aH2, nH1, nH2, (aH1 + aH2) - (nH1 + nH2)));
    2.0f * ((aH1 + aH2) - (nH1 + nH2))
  }
  def Fperiod(w : String) = 1.0f + B.findAllIn(w).toList.length.toFloat

  def Flength(w : String, end : String) = 1.0f / math.exp((w.length + end.length).toFloat).toFloat

  def Fpenalty(w : String) = 1.0f / math.pow(w.length.toFloat, (cA.getOrElse(w, 0) - cAB.getOrElse(w, 0)).toFloat)

  //def tokenContext(tokens : Iterable[Token]) = (Array(boundaryToken).view ++ tokens ++ Array(boundaryToken).view).sliding(3);
  def tokenContext(tokens : Seq[Token]) = (boundaryToken +: tokens :+ boundaryToken).sliding(3)

  // Assumes that each line end is a sentence boundary (but there may be other, line-internal sentence boundaries too).
  def learn(data : Iterable[String]) {
    val tcA    = HashMap[String, Int]() withDefaultValue 0
    val tcAB   = HashMap[String, Int]() withDefaultValue 0
    val tsA    = HashMap[String, Int]() withDefaultValue 0
    val tsAB   = HashMap[String, Int]() withDefaultValue 0
    val tcPN   = HashMap[(String, String), Int]() withDefaultValue 0

    // Compute first pass stats
    println("Doing first pass")
    for ((Seq(prev, w, next), idx) <- tokenContext(data.toSeq.flatMap(l => tokenizer(l.trim))).zipWithIndex) {
      w.word match {
          case B(_) => 
            tcAB(prev.word) += 1
            tcPN(prev.word -> next.word) += 1
            cB += 1
            tcA(w.word) += 1;
          case _ => tcA(w.word) += 1;
      }
      //if (idx != 0 && idx % 100000 == 0) print(".");
      //if (idx != 0 && idx % 10000000 == 0) println(" %20d" format idx);
      N += 1
    }
    cA  = Map[String, Int](tcA.toSeq : _*)
    cAB = Map[String, Int](tcAB.toSeq : _*)
    cPN = Map[(String, String), Int](tcPN.toSeq : _*)

    // compute starters
    println("Doing second pass")
    //for (l <- data.map(_.trim); Seq(prev, w, next) <- tokenContext(findabbr(l)))
    for (Seq(prev, w, next) <- data.flatMap(l => tokenContext(findabbr(l.trim))))
      if (w.kind == 'EndSent || (w.kind == 'Abbr && next.kind == 'Boundary)) {
        tsAB(next.word) += 1
        sB += 1
      }
    sA  = Map[String, Int](tsA.toSeq : _*)
    sAB = Map[String, Int](tsAB.toSeq : _*)
  }
  def findabbr(s : String) : Seq[Token] = {
    val tokens = tokenizer(s)
    if (tokens.length == 0) Seq[Token]()
    else
    tokenContext(tokens).toSeq map { 
      case Seq(prev, w, next) =>
        w.word match {
          case B(_) => 
            val abbrScore = binomLLR(cAB.getOrElse(prev.word, 0).toFloat, cA.getOrElse(prev.word, 0).toFloat) * Fperiod(prev.word) * Flength(prev.word, w.word) * Fpenalty(prev.word)
            if (abbrScore > abbrThreshold) w.copy(kind = 'Abbr)
            else w.copy(kind = 'EndSent)
          case _ => w
      }
    }
  }
  def seg(orig : String, tokens : Iterable[Token]) : Array[String] = {
    val ret = ArrayBuffer[String]()
    var lastend = -1
    for (Seq(prev, t, next) <- tokenContext(tokens.toSeq)) {
      if ((t.kind == 'Abbr && next.word(0).isUpper && next.kind != 'Abbr) || (t.kind == 'EndSent)) {
        if (t.kind == 'Abbr) {
          val collocScore = sStart(cPN.getOrElse(prev.word -> next.word, 0).toFloat, cA.getOrElse(next.word, 0).toFloat, cA.getOrElse(prev.word, 0).toFloat)
          val startScore  = sStart(sAB.getOrElse(next.word, 0).toFloat, sB, cA.getOrElse(next.word, 0).toFloat)
          if (collocScore <= colThreshold)
            if (startScore > ssThreshold) {
              //println("%-7s %5s %20s = [starter: %12.2f, colloc: %12.2f]" format (prev.word, t.word, next.word, startScore, collocScore));
              ret += orig.substring(lastend + 1, t.endpos).trim
              lastend = t.endpos
            }
        }
        else if (next.word(0).isUpper || ((next.word matches """\p{Pf}""") && """[\u201c\u201d\"]""".r.findAllIn(orig.substring(lastend + 1, t.endpos)).length % 2 == 0)) {
          //println("END-SENT: (%4d -> %4d: %s [%s])" format (lastend + 1, t.endpos, orig.substring(lastend + 1, t.endpos).trim, next.word));
          ret += orig.substring(lastend + 1, t.endpos).trim
          lastend = t.endpos
        }
      }
    }
    if (lastend != orig.length) ret += orig.substring(lastend + 1, orig.length).trim
    ret.toArray
  }
  def apply(s : String) = seg(s, findabbr(s))
}
object SentenceSegmenter {
  val SentenceBoundary = """([.\s]+|[\u3002\u06d4\u2026?!]\"?)"""
  val Number           = """^\d+$""".r
  val WB               = ("""^(.*?)""" + SentenceBoundary + "$").r
  val Delim            = """[^\s,;\"]+(?=([\s,;\"]|$))""".r

  def compress(s : Array[Token]) : Array[Token] = {
    if (s.length == 0) s
    else (s zip s.drop(1)).flatMap { case (p, c) => if (p.word == c.word && c.word.matches("^[.!?]+$")) Array[Token]() else Array(p) } ++ Array(s.last)
  }
  def segToken(l : String) = {
    def findToken(s : String) = Delim.findFirstMatchIn(s) match { case Some(m) => (m.start + m.matched.length, m.matched, m.after.toString); case None => (-1, "", "") }
    def makeToken(word : String, end : Int) = word match {
        case Number(num) => Array(Token("<number>", end));
        case WB("", punct) => Array(Token(punct, end));
        case WB(word, punct) => Array(Token(word, end - punct.length), Token(punct, end));
        case _ => Array(Token(word, end));
    }
    var ret = ArrayBuffer[Token]()
    var (end, word, res) = findToken(l.replaceAll("""[\u2019\u2018`]""", "'").replaceAll("""[\u201c\u201d]""", "\"")); // one-to-one character conversions ONLY!

    while (res != "") {
      ret ++= makeToken(word, end)
      val (nend, nword, nres) = findToken(res)
      end += nend; word = nword; res = nres
    }
    if (word != "") ret ++= makeToken(word, end)
    //println("tokens: " + ret.map(_.word));
    compress(ret.toArray)
  }
}

// -------------------------------------------------------------------------------------------------------------------------------------
// AP5 : Arabic tokenizer
// -------------------------------------------------------------------------------------------------------------------------------------
class AP5 extends Function1[String, Array[String]] {
  val tatwheel = """\u0640"""
  val diacritics = """[\u064b-\u0652]"""
  val forwardslash = """[\u002f]"""
  val parens = """[\u0028\u0029]"""

  def diaPlus(in : String) : String = {
    var ret = in.replaceAll(tatwheel, "").replaceAll(diacritics, "").replaceAll(forwardslash, " ")
    // Dash Processing
    ret = ret.replaceAll("""[\u2010\u2011]""", """\u002d""").replaceAll("""[\u2012-\u2015]""", """ \u002d """)
    // Other punctuation
    ret = ret.replaceAll(parens, "").replaceAll("""\u002c""", """\u060c""").replaceAll("""\u003a""", """\u060c""")
    // Normalize alef hamza forms to bare alef
    ret = ret.replaceAll("""(?:\u0623|\u0625)""", """\u0627""")
    ret.replaceAll("""\\u003f""", """\u061f""")
  }
  def hamzaNorm(word : String) : String = word.replaceAll("""^(\u0622|\u0623|\u0625|\u0627)""", """\u0627""")

  def tanweenFilt(word : String) : String = word.replaceAll("""(\u064d|\u064c|\u064b)$""", "")

  def waalFilt(word : String) : String =
    word.replaceFirst("""\b\u0648\u0627\u0644\p{Alpha}*""", """\u0648 \u0627\u0644 """)
        .replaceFirst("""\b\u0648\p{Alpha}*""", """\u0648 """).replaceFirst("""\b\u0627\u0644\p{Alpha}*""", """\u0627\u0644 """)

  def sepPrep(word : String) : String = {
    var ret = word
    // Separate leading attached words
    if ("""\b\u0628\u0627\u0644.+""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <bi-al-word> to <bi al word>
      ret = ret.replaceFirst("""\b\u0628\u0627\u0644""", """\u0628 \u0627\u0644 """)
    else if ("""\b\u0628.+""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <bi-word> to <bi word>
      ret = ret.replaceFirst("""\b\u0628""", """\u0628 """)
    else if ("""\b\u0641\u0627\u0644.+""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <fa-al-word> to <fa al word>
      ret = ret.replaceFirst("""\b\u0641\u0627\u0644""", """\u0641 \u0627\u0644 """)
    else if ("""\b\u0641.+""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <fa-word> to <fa word>
      ret = ret.replaceFirst("""\b\u0641""", """\u0641 """)
    else if ("""\b\u0643\u0627\u0644.+""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <ka-al-word> to <ka al word>
      ret = ret.replaceFirst("""\b\u0643\u0627\u0644""", """\u0643 \u0627\u0644 """)
    else if ("""\b\u0643.+""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <ka-word> to <ka word>
      ret = ret.replaceFirst("""\b\u0643""", """\u0643 """)
    else if ("""\b\u0644\u0644.+""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <lil-word> to <li al word>
      ret = ret.replaceFirst("""\b\u0644\u0644""", """\u0644 \u0627\u0644 """)
    else if ("""\b\u0644.+""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <li-word> to <li word>
      ret = ret.replaceFirst("""\b\u0644""", """\u0644 """)

    ret
  }
  def pronounFilt(word : String) : String = {
    var ret = word

    if (""".+\u0646\u064A\b""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <word-niy> to <word niy>; 1st person singular verbal attached pronoun
      ret = ret.replaceFirst("""\u0646\u064A\b""", """ \u0646\u064Apost""")
    else if(""".+\u064A\b""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <word-y> to <word y>; 1st person singular nominal attached pronoun
      ret = ret.replaceFirst("""\u064A\b""", """ \u064Apost""")
    else if(""".+\u0643\b""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <word-ka> and <word-ki> to <word ka/ki>; 2nd person singular masculine & feminine pronouns
      ret = ret.replaceFirst("""\u0643\b""", """ \u0643post""")
    else if(""".+\u0647\b""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <word-hu> to <word hu>; 3nd person singular masculine pronoun
      ret = ret.replaceFirst("""\u0647\b""", """ \u0647post""")
    else if(""".+\u0647\u0627\b""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <word-hA> to <word hA>; 3nd person singular feminine pronoun
      ret = ret.replaceFirst("""\u0647\u0627\b""", """ \u0647\u0627post""")
    else if(""".+\u0643\u0645\u0627\b""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <word-kamA> to <word kamA>; 2nd person dual masculine & feminine pronouns
      ret = ret.replaceFirst("""\u0643\u0645\u0627\b""", """ \u0643\u0645\u0627post""")
    else if(""".+\u0647\u0645\u0627\b""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <word-humA> to <word humA>; 3nd person dual masculine & feminine pronouns
      ret = ret.replaceFirst("""\u0647\u0645\u0627\b""", """ \u0647\u0645\u0627post""")
    else if(""".+\u0646\u0627\b""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <word-nA> to <word nA>; 1st person plural pronoun
      ret = ret.replaceFirst("""\u0646\u0627\b""", """ \u0646\u0627post""")
    else if(""".+\u0643\u0645\b""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <word-kum> to <word ka>; 2nd person plural masculine pronoun
      ret = ret.replaceFirst("""\u0643\u0645\b""", """ \u0643post""")
    else if(""".+\u0643\u0646\b""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <word-kunna> to <word ka>; 2nd person plural feminine pronoun.
      // Note that the "nn" (a doubled nUn) is almost always realized as nUn
      // followed by a shadda.  Since shadda is rarely written, the pronoun appears
      // as if it has a single nUn.
      ret = ret.replaceFirst("""\u0643\u0646\b""", """ \u0643post""")
    else if(""".+\u0647\u0645\b""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <word-hum> to <word hum>; 3nd person plural masculine pronoun
      ret = ret.replaceFirst("""\u0647\u0645\b""", """ \u0647\u0645post""")
    else if(""".+\u0647\u0646\b""".r.findFirstIn(ret) match { case Some(x) => true; case _ => false })
      // Change <word-hunna> to <word hum>; 3rd person plural feminine pronoun.
      // Note that the "nn" (a doubled nUn) is almost always realized as nUn
      // followed by a shadda.  Since shadda is rarely written, the pronoun appears
      // as if it has a single nUn.
      ret = ret.replaceFirst("""\u0647\u0646\b""", """ \u0647\u0645post""")

    ret
  }
  def anorm(word : String) : String = {
    val proc = List(hamzaNorm _, tanweenFilt _, waalFilt _, sepPrep _, pronounFilt _)
    var ret = word
    for (p <- proc) ret = p(ret)
    ret
  }
  def apply(in : String) : Array[String] = {
    val words = diaPlus(in).trim.split("""\s+""")
    words.map(anorm(_)); //.mkString(" ").trim.replaceAll("""\s+""", " ");
  }
}

// -------------------------------------------------------------------------------------------------------------------------------------
// English Tokenizer : For IWSLT
// -------------------------------------------------------------------------------------------------------------------------------------
trait SimpleTokenizer extends Function1[String, Array[String]] {
  def recSplit(word : String, pat : Regex, postP : Boolean) : String = {
    var nw = word
    var (pres, posts) = (List[String](), List[String]())
    var m = pppunct.pattern.matcher(nw)
    var stopP = nbList(nw.toLowerCase)
    while (!stopP && m.matches && m.group(1).length + m.group(3).length > 0) {
      val (lc, rc, mid) = (m.group(1) + m.group(2), m.group(2) + m.group(3), m.group(2))
      if (nbList(lc.toLowerCase)) { stopP = true; nw = lc; if (m.group(3).length > 0) posts ::= m.group(3); }
      else if (nbList(rc.toLowerCase)) { stopP = true; nw = rc; if (m.group(1).length > 0) pres ::= m.group(1); }
      else { stopP = nbList(mid.toLowerCase); nw = mid; if (m.group(1).length > 0) pres ::= m.group(1); if (m.group(3).length > 0) posts ::= m.group(3); }
      m = pppunct.pattern.matcher(nw)
    }

    (if (pres.length > 0) pres.mkString(" ") + " " else "") + nw + (if (posts.length > 0) " " + posts.mkString(" ") else "")
  }

  def apply(in : String) : Array[String] = {
    var ret = in.replaceAll("""\|""", " ").replaceAll("""\\\s*""", """\\""").replaceAll("""\s+""", " ").trim
    val cr = Perl5Parser.makeReplacer("""tr/[\x{ff01}-\x{ff5e}]/[!-~]/""")
    ret = cr.doReplacement(ret)
    var m = digitspace.pattern.matcher(ret)
    while (m.matches)
    {
      ret = m.group(1) + m.group(2)
      m = digitspace.pattern.matcher(ret)
    }

    var words = List[String]()
    for (w <- ret.replaceAll("(\u201c|\u201d)", "\"").replaceAll("\u2122", " (tm) ").replaceAll("\u00a9", " (c) ").replaceAll("(\u2018|\u2019|\u0060|\u2032)", "'")
                 .replaceAll("(\u201c|\u201d|\u300e|\u300f)", "\"").replaceAll("(\u2011|\u2013|\u2014)", "-")
                 .replaceAll("(\u2026|\\.\\.+)", " ... ").replaceAll("""\p{C}""", "").replaceAll("-+", " ").trim.split(defaultSpace)) {
      var nw = recSplit(w, prepunct, false); // added 9/21/10: split off pre-punct
      words ::= nw
    }
    
    val news = words.reverse.mkString(" ").replaceAll("""\.(\s+\.)+""", "...").split("""\s+""").map { x => if (replacement isDefinedAt x) replacement(x) else x }.mkString(" ").replaceAll("""' s\b""", "'s")
    news.split("""\s+""")
  }

  // Abbreviation list
  val nbList : Set[String]
  val replacement : Map[String, String]
  val digitspace = """^(.*\d)\s+(\d.*)$""".r

  val pppunct   = """^(\p{P}?)(.+?)(\p{P}?)$""".r
  val prepunct  = """^(\p{P})(.+)$""".r
  val postpunct = """^(.+)(\p{P})$""".r
}

// -------------------------------------------------------------------------------------------------------------------------------------
// English Tokenizer : For IWSLT
// -------------------------------------------------------------------------------------------------------------------------------------
class EnglishToken extends Function1[String, Array[String]] with SimpleTokenizer { 
  override val nbList = Set("mr.", "mrs.", "ms.", "mme.", "dr.", "a.m.", "p.m.", "u.s.", "e.u.", "i.d.", "d.c.", "t.b.", "p.s.", "m.s.", "m.a.", 
                            "a.", "b.", "c.", "d.", "e.", "f.", "g.", "h.", "i.", "j.", "k.", "l.", "m.", "n.", "o.", "p.", "q.", "r.", "s.", "t.", 
                            "u.", "v.", "w.", "x.", "y.", "z.", "etc.", "e.t.", "a.d.", "b.c.", "e.g.", "i.e.",  "'em", "...", "....", "--", "'bout", "'s")
  override val replacement = HashMap("'bout" -> "about", "'em" -> "them")
}

// -------------------------------------------------------------------------------------------------------------------------------------
// Turkish Morphological Analyzer : (TODO: Post IWSLT)
// -------------------------------------------------------------------------------------------------------------------------------------
class TurkMorph extends Function1[String, Array[String]] {
  def apply(in : String) : Array[String] = {
    var ret = in.replaceAll("-", " ").replaceAll("'", " ' ").replaceAll("""\s+""", " ")
    ret.split("""\s+""")
  }
}

// -------------------------------------------------------------------------------------------------------------------------------------
// French Tokenization
// -------------------------------------------------------------------------------------------------------------------------------------
class FrenchToken extends SimpleTokenizer { 
  override val nbList = Set("m.", "mme.", "dr.", "d.c.", "...", "....", "--", "j'", "n'", "d'", "s'", "c'", "m'", "qu'", "l'",
                            "a.", "b.", "c.", "d.", "e.", "f.", "g.", "h.", "i.", "j.", "k.", "l.", "m.", "n.", "o.", "p.",
                            "q.", "r.", "s.", "t.", "u.", "v.", "w.", "x.", "y.", "z.")
  override val replacement = HashMap[String, String]("j'" -> "je", "n'" -> "ne", "d'" -> "de", "s'" -> "se", 
                                                     "c'" -> "ce", "m'" -> "me", "qu'" -> "que", "l'" -> "le")

  val APMatcher = """(?iu)^(l|c|s|qu|m|n|j|d)'(.*)$""".r
  val TIn = """(?iu)^(.*)-t-(.*)$""".r

  override def apply(in : String) : Array[String] = {
    var words = List[String]()
    for (w <- in.replaceAll("(\u201c|\u201d)", "\"").replaceAll("\u2122", " (tm) ").replaceAll("\u00a9", " (c) ").replaceAll("(\u2018|\u2019|\u0060|\u2032)", "'")
                .replaceAll("(\u201c|\u201d|\u300e|\u300f)", "\"").replaceAll("(\u2026|\\.\\.+)", " ... ")
                .replaceAll("(\u2011|\u2013|\u2014)", "-").replaceAll("""\p{C}""", "").trim.split(defaultSpace)) {
      var nw = recSplit(w, prepunct, false)
      words ::= nw
    }

    val news = words.reverse.mkString(" ").replaceAll("""\.(\s+\.)+""", "...").split("""\s+""").map { w =>
                                                             var nw = w match { case APMatcher(pre, post) => pre + "e " + post; case _ => w; }
                                                             nw match { case TIn(pre, post) => pre + " " + post; case _ => nw; }
                                                           }.mkString(" ").replaceAll("-+", " ").replaceAll("""\s+""", " ").trim
    news.split("""\s+""")
  }
}

// -------------------------------------------------------------------------------------------------------------------------------------
// Korean Tokenization
// -------------------------------------------------------------------------------------------------------------------------------------
class KoreanToken extends Function1[String, Array[String]] with SimpleTokenizer { 
  override val nbList = Set("m.", "mme.", "dr.", "d.c.", "...", "....", "--", "j'", "n'", "d'", "s'", "c'", "m'", "qu'", "l'",
                            "a.", "b.", "c.", "d.", "e.", "f.", "g.", "h.", "i.", "j.", "k.", "l.", "m.", "n.", "o.", "p.", 
                            "q.", "r.", "s.", "t.", "u.", "v.", "w.", "x.", "y.", "z.")
  override val replacement = HashMap[String, String]("j'" -> "je", "n'" -> "ne", 
                                                     "d'" -> "de", "s'" -> "se", 
                                                     "c'" -> "ce", "m'" -> "me", 
                                                     "qu'" -> "que", "l'" -> "le")

  val NumMatcher = """(?iu)^(\S*?)(\p{P}*[A-Z0-9-_]+\p{P}*)(\S*?)$""".r
  val PcMatcher = """(?iu)^(.+?)(\p{P}+)(.?+)$""".r

  override def apply(in : String) : Array[String] = {
    var words = List[String]()
    for (w <- in.replaceAll("(\u201c|\u201d)", "\"").replaceAll("\u2122", " (tm) ").replaceAll("\u00a9", " (c) ").replaceAll("(\u201c|\u201d|\u300e|\u300f)", "\"")
              .replaceAll("(\u3145|\u3146|\u3160|\u314b)", " ").replaceAll("(\u2026|\\.\\.+)", " ... ").replaceAll("(\u266a|\u266b)", " ")
              .replaceAll("(\u2018|\u2019|\u0060|\u2032)", "'").replaceAll("(\u2011|\u2013|\u2014|\u3161)", "-").replaceAll("""\p{C}""", "").trim.split(defaultSpace)) {
      val cut = w match { 
        case NumMatcher(start, num, end) => 
          if (start == "" && end == "") List(num)
          else if (start == "") List(num, end)
          else if (end == "") List(start, num)
          else List(start, num, end);
        case PcMatcher(start, p, end) => List(start, p, end);
        case _ => List(w);
      }

      for (ws <- cut) {
        var nw = recSplit(ws, prepunct, false)
        words ::= nw
      }
    }

    val news = words.reverse.mkString(" ").replaceAll("""\.(\s+\.)+""", "...").split("""\s+""").mkString(" ").replaceAll("-+", " ").replaceAll("""\s+""", " ").trim
    news.split("""\s+""")
  }
}
