/**
 * Args.scala
 *
 * @author Wade Shen <BR>
 * <tt><a href=mailto:swade@ll.mit.edu>swade@ll.mit.edu</a></tt> <BR>
 * Copyright &copy; 2009 Massachusetts Institute of Technology, Lincoln Laboratory
 * @version 0.2
 */
package mitll
import utilities._
import java.io.{FileReader, Reader, PrintStream, FileOutputStream, File};
import util.parsing.combinator.RegexParsers;
import collection.mutable.{HashMap, ArrayBuffer, ListBuffer, LinkedHashMap, Map};
import org.apache.commons.lang3.text.WordUtils._

// Basic exception types
case class ExitException(reason : String) extends Exception; // Non-real exception, from things like the usage message
case class Fatal(reason : String) extends Exception { override def getMessage = reason; }
case class IllegalValue(reason : String) extends Exception { override def getMessage = reason; }

/**
 * ==Reader/writer pair for Args of different types==
 * Each `Arg[T]` must define a reader/writer.
 * @tparam T this type is bound to specific r/w functions.  If this is done via implicits in the companion, then this binding will be the default.
 * @param t type name for debugging
 * @param r a function reading a string and generating an instance type T, this should to static validation (i.e. syntactic validation, fixed sets, etc.)
 * @param w convert type T to a string */
// -------------------------------------------------------------------------------------------------------------------------------------
case class ArgRW[T](t : String, r : (String) => T, w : (T) => String = { (e : T) => e.toString });
object ArgRW {
  def objconvert[C](a : AnyRef, t : Class[C]) : C = a.asInstanceOf[C]; //t match {
//    case String.getClass  => a.asInstanceOf[C];
    //case ClassOf[Int]     => a.asInstanceOf[Int];
    //case ClassOf[Float]   => a.asInstanceOf[Float];
    //case ClassOf[Boolean] => a.asInstanceOf[Boolean];
    // case "scala.collection.immutable.Set$Set1"      => a.asInstanceOf[Set[String]];
    // case "scala.collection.immutable.Set$EmptySet$" => a.asInstanceOf[Set[String]];
    // case "scala.collection.immutable.$colon$colon"  => a.asInstanceOf[List[(String, String)]];
    // case "scala.collection.immutable.Nil$"          => a.asInstanceOf[List[(String, String)]];
//    case x @ _ => assert(false, "argument type: " + x + " is unsupported");
//  }
  implicit val readString    = ArgRW("string", s => s, w = (x : String) => if (x != null) x else "null"); //{ (s : String) => if (s matches """(?s).*\n.*""") "{\n" + s + "\n}\n" else s });
  implicit val readInt       = ArgRW("int", s => s.toInt);
  implicit val readFloat     = ArgRW("float", s => s.toFloat);
  implicit val readBoolean   = ArgRW("boolean", s => s.toBoolean);
  implicit val readSet       = ArgRW("set", s => Set[String]((if (s.trim == "") Array[String]() else s.trim.split("""(\s|,|;)+""")) : _*), (s : Set[String]) => s.mkString(" "));
  implicit val readTupleList = ArgRW("tuple-list", s => if (s.trim == "") List[(String, String)]()
                                                        else List[(String, String)](
                                                          s.trim.split("""\s*,\s*""").map { 
                                                            x => val a = x.split("""\s*(->)\s*"""); 
                                                            assert(a.length == 2, "Unable to parse (" + x + ") as a list of tuples (e.g a -> b, c -> d)");
                                                            a(0) -> a(1) } : _*), (s : List[(String, String)]) => s.mkString(", "));
}

/**
 * ==Argument wrapper with reader/writer and help for data fields that are settable at runtime ==
 * @tparam T Type of value
 * @param getter       function to get the value of interest
 * @param setter       funciton to set the 
 * @param help         a message for user describing the usage
 * @param rw           reader/writer for this argument default values are determined by type (see [[Utils.ArgRW]]) */
// -------------------------------------------------------------------------------------------------------------------------------------
case class Arg[T](getter : () => T, setter : (T) => Unit,  help : String = "")(implicit val rw : ArgRW[T]) { 
  def <<(s : String) { val x = rw.r(s); value = x; } // validation now in field getter/setter
  override def equals(r : Any) : Boolean = { value == r; }
  override def toString = rw.w(value);
  def usage = help + " [default: " + rw.w(value) + "]";
  // unpackers
  def get : T = value; //if (rw.v(value)) value else throw Fatal("Holy crap, the validator failed, but didn't throw!");
  def b = value.asInstanceOf[Boolean];
  def i = value.asInstanceOf[Int];
  def f = value.asInstanceOf[Float];
  def s = value.toString;
  def apply() = value;
  def value = getter();
  def value_=(set : T) { setter(set); }
  def update(newval : T) { value = newval; }
  def :=(newval : T) { update(newval); }
}
object Arg {
  class blob[T](var holder : T);
  def apply[T](value : T, help : String)(implicit rw : ArgRW[T]) = {
    val r = new blob[T](value);
    new Arg(r.holder _, r.holder_= _, help)(rw);
  }
}

case class Settings(section : String, settings : Map[String, String]);
/**
 * ==Reader for settings in a config file format== */
// -------------------------------------------------------------------------------------------------------------------------------------
trait SettingsReader extends RegexParsers {
  override val whiteSpace = """([\t ]*(?<!\\)#[^\n\r$]+|[\t ]+)""".r;

  def EOL         = rep("""[\n\r$]+""".r);
  def sectName    = "[" ~> """[^\]]+""".r <~ "]" <~ opt(EOL);
  def sect        = opt(EOL) ~> sectName ~ settings <~ opt(EOL) ^^ { case name ~ settings => Settings(name, HashMap(settings : _*)); }
  def sects       = rep(sect);
  def vl          = """(?m)([^\n\r#]|(?<=\\)#)*""".r <~ opt(EOL) ^^ { a => a.replaceAll("""\\#""", "#").trim }
  def vlcont      = """(?m)([^\n\r#}]|(?<=\\)#)+""".r <~ opt(EOL) ^^ { a => a.replaceAll("""\\#""", "#").trim }
  def value       = ("{" ~ opt(EOL) ~> rep1(vlcont) <~ "}" <~ opt(EOL) ^^ { case a => a.mkString("\n"); } |
                     vl);
  def setting     = opt(EOL) ~> ("""([^:=\s#]|(?<=\\)#)+""".r <~ """(:|=)""".r) ~ value ^^ { case k ~ v => k -> v }
  def settings    = rep(setting);

  def read(stream : Reader) = parseAll(sects, stream) match {
    case Success (ls, _) => ls;
    case err @ _ => assert(false, "Parse error: " + err); null
  }
}

/**
 * ==Parameter Hash== */
// -------------------------------------------------------------------------------------------------------------------------------------
class Params[T >: Null] extends LinkedHashMap[String, T] {
  def ident(a : T) : Any = a;
  def kv(k : String, v : String) : (String, (String, (Arg[_]) => String)) = k -> (k, { a => v });
  override def clone : Params[T] = { val r = new Params[T]; for (i <- this) r += i; r }
  def defaultMap : Map[String, (String, (T) => Any)] = {
    val ks = keys.toList.sortWith(_ < _);
    HashMap[String, (String, (T) => Any)]((ks zip (ks zip (for (i <- ks) yield ident _))) : _*);
  }
  override def toString = this.map { case (k, v) => k + "=" + v }.mkString(", ");
  def print(name : String, format : String) : String = print(name, defaultMap, format);
  def print(name : String, map : Map[String, (String, (T) => Any)], format : String) : String = {
    val formatter = if (format == "ini") "[%s]\n%s\n"
                    else if (format == "cfg" || format == "colon") "%s: %s\n"
                    else if (format == "tab") "%s\t%s\n"
                    else if (format == "params") "%s = %s\n"
                    else { assert(false, "ERROR: Unknown format -- " + format); "" };

    //val nullT : T = null;
    withPrint(name) { f => map.foreach { case (k, (t, lambda)) => f.printf(formatter, t, lambda(getOrElse(k, null)).toString); } }
    return name;
  }
}
object Params {
  //def apply[T >: Null](kv : (String, T)*) = { val ret = new Params[T]; for (p <- kv) ret += p; ret; }
  def make[T >: Null](kv : (String, T)*) = { val ret = new Params[T]; for (p <- kv) ret += p; ret; }
  def apply(kv : (String, Arg[_])*) = { val ret = new Params[Arg[_]]; for (p <- kv) ret += p; ret; }
}

/**
 * ==Program configuration==
 * This defines a set of arguments organized as [[Utils.Params]].  This class handles arguments as short names (e.g. -parameter).
 * If multiple sections have parameters with the same name, supplying -shortname XX sets all instances of this parameter to XX.
 * Long parameters like -section_name:parameter XX correspond to [section name]\nparameter: XX */
// -------------------------------------------------------------------------------------------------------------------------------------
class Configuration extends Iterable[(String, Params[Arg[_]])] with SettingsReader {
  val SharedSection = "Shared Parameters";
  val sections = LinkedHashMap[String, Params[Arg[_]]](); //"Global Options" -> new Params[Arg[_]]);
  lazy val shortnames = shortindex;

  // Index short names
  def shortindex : Map[String, List[(String, String)]] = {
    val ret = LinkedHashMap[String, ListBuffer[(String, String)]]();
    for ((section, params) <- sections; (k, v) <- params)
      ret.getOrElseUpdate(k, ListBuffer[(String, String)]()) += section -> k;
    for ((k, v) <- ret) yield k -> v.toList;
  }

  // Utilities
  def cmd2section(cmd : String) = cmd.replaceAll("""_""", " ");
  def section2cmd(section : String) = section.replaceAll(""" """, "_");
  val Long = """^([^.]+):+(.*)""".r;
  def normalize(shortorlong : String) = shortorlong match {
    case Long(section, param) => List(cmd2section(section) -> param);
    case short @ _ => shortnames(short); // ls should not throw
  }

  // forwarders to underlying map
  def keys = sections.keys;
  def iterator = sections.iterator;
  def +(sp : (String, Params[Arg[_]])) = { sections += sp._1 -> sp._2; this; }
  def isDefinedAt(section : String, k : String) : Boolean = sections.isDefinedAt(section) && sections(section).isDefinedAt(k);
  def isDefinedAt(shortorlong : String) : Boolean = shortorlong match {
    case Long(section, param) => isDefinedAt(section, param);
    case short @ _ => shortnames isDefinedAt short;
  }
  def apply(section : String, k : String) : Arg[_] = sections(section)(k);
  def apply(shortorlong : String) : List[Arg[_]] = {
    assert(isDefinedAt(shortorlong), "Can't find parameter: " + shortorlong);
    val possibles = normalize(shortorlong);
    possibles map { case (section, param) => apply(section, param); }
  }
  def update(section : String, params : Params[Arg[_]]) { 
    assert(!section.matches(""".*[_.].*"""), "section name cannot have '_' or '.' characters: " + section);
    sections(section) = params;
  }
  def update(section : String, param : String, value : String) {
    assert(isDefinedAt(section, param), "Cannot find Section (" + section + ") or parameter (" + param + ")");
    try { sections(section)(param) << value; } catch { 
      case IllegalValue(e) => throw Fatal("Can't set parameter (" + param + ", type: " + sections(section)(param).rw.t + ") to (" + value + ") because the this value is invalid!"); 
      case e : java.lang.NumberFormatException => throw Fatal("Can't convert (" + value + ") to parameter (" + param + ", type: " + sections(section)(param).rw.t + ")"); 
    }
  }
  def update(shortorlong : String, value : String) {
    assert(isDefinedAt(shortorlong), "Can't find parameter: " + shortorlong);
    val possibles = normalize(shortorlong);
    for ((section, param) <- possibles) update(section, param, value);
  }
  def readSettings(fn : String) {
    assert(fn.existe, "The config file (" + fn + ") doesn't exist");
    assert(fn.canRead, "The config file (" + fn + ") isn't readable");
    val f = new FileReader(fn);
    val config = try read(f) finally f.close;
    for (Settings(section, settings) <- config; if (section != SharedSection); (k, v) <- settings) this(section, k) = v;
    // Shared parameters override other defaults
    for (Settings(section, settings) <- config; if (section == SharedSection); (k, v) <- settings) this(k) = v;
   }
  def print(p : PrintStream, comment : Set[String] = Set[String]()) {
    def sectioner(sname : String, comment : String = "", pre : String = "") { p.println; p.println(pre + "[" + sname + "]" + comment); }
    p.println("# Begin Configuration");

    // psuedo section
    val longest = " " * 20 + shortnames.keys.foldLeft(SharedSection) { case (a, b) => if (a.length > b.length) a else b };
    val pad = longest.replaceAll(".", " ");
    sectioner(SharedSection, pad.substring(SharedSection.length + 2) + "# These settings take precidence over the section-specific settings below");
    for ((short, sps) <- shortnames; if (sps.length > 1)) {
      val values = sps.map { case (sec, p) => this(sec, p).toString }
      val valset = values.distinct;
      val pre = if (valset.length == 1) short + ": " + valset.head + pad.substring(math.min(short.length + 2 + valset.head.length, pad.length))
                else "# " + short + ": " + pad.substring(math.min(short.length + 4, pad.length))
      val sets = wrap(sps.map { case (sec, p) => section2cmd(sec) + ":" + p + " (" + this(sec, p) + ")" }.mkString(", "), 80).split("""\n+""");
      val (spacer1, spacer2) = (" " * pre.length, " " * 7);
      p.println(pre + "# sets: " + sets(0));
      for (i <- 1 until sets.length) p.println(spacer1 + "#" + spacer2 + sets(i));
    }
  
    for ((section, params) <- sections) {
      val pre = if (comment(section)) "# " else "";
      sectioner(section, "", pre);
      for ((k, v) <- params) {
        val value = if (v.toString matches """(?s).*\n.*""") "{\n" + pre + v.toString + "\n" + pre + "}\n" else pre + v.toString;
        p.println(pre + k + ": " + value);
      }
    }
    p.println; p.println("# End Configuration");
  }
  def write(fn : String, comment : Set[String] = Set[String]()) = { withPrint(fn)(f => print(f, comment)); fn; }
}

/**
 * ==Settings holder with command line parser==
 * Clients must define `program` : a string used to print usage
 * Configuration files are parsed and set. */
// -------------------------------------------------------------------------------------------------------------------------------------
trait ArgHandler extends RegexParsers {
  val program : String;
  var config = new Configuration;
  def usage(p : PrintStream = System.err) {
    val longest = config.flatMap { case (k, v) => v.map(_._1) }.foldLeft("") { case (a, b) => if (a.length > b.length) a else b };
    val spaces = longest.replaceAll(".", " ");
    p.println("usage: " + program + " [options...] args\n");
    for ((section, params) <- config) {
      p.println(section); //config.section2cmd(section));
      p.println(augmentString("=") * section.length);
      for ((k, v) <- params)
        p.println("       -" + k + spaces.substring(k.length) + " : " + v.usage); }
    //config.print(p);
  }
  def checkedSet(a : String, v : List[String]) {
    assert(config isDefinedAt a, "Unknown argument: " + a);
    if (!Set("set", "tuple-list")(config(a).head.rw.t)) assert(v.length == 1, "argument of type (%s) cannot have multiple values (%s)" % (config(a).head.rw.t, v.mkString(" ")));
    config(a) = v.mkString(" ");
  }
  val ArgS   = """^--?([^\s-]\S*)""".r;
  val Xx     = """^((?!-)(?!-)\S+)""".r;
  val String = """^'''(.*?)'''""".r;
  val NonArg = """^(\S+)""".r;
  val pred   = Set("C", "config", "configuration", "cfg", "help", "h", "?");
  def arg    = ArgS ^? { case ArgS(x) if (config.isDefinedAt(x) || pred(x)) => /* println("-ARG : " + x); */ x; }
  def nonarg = NonArg ^^ { case NonArg(x) => println("Xtra nonarg : " + x); x } |
               String ^^ { case String(s) => println("Xtra string : " + s); s }
  def value  = String ^^ { case String(s) => println("value string : " + s); s } |
               ArgS ^? { case y @ ArgS(x) if (!config.isDefinedAt(x) && !pred(x)) => /* println("value nonArg : " + y); */ y; } |
               Xx ^^ { case Xx(x) => /* println("value literal : " + x); */ x.toString } //Xx ^^ { case Xx(x) if (x != "--") => println("value : " + x); x case _ => Failure()} |
  def cline  = rep1(arg ~ rep(value)) ~ "--" ~ rep(nonarg) ^^ { case flags ~ "--" ~ strings => flags -> strings } | // -x -y v -z a -- f1 f2...
               rep1(arg ~ rep(value)) ^^ { case flags @ _ => flags -> Nil } | // -x -y v -z a...
               rep(nonarg) ^^ { case strings @ _ => Nil -> strings }  // -x -y v -z a -- f1 f2...
  def parseArgs(args : Array[String], out : => PrintStream = System.err) = {
    parseAll(cline, args.map(x => if (x matches """^.*\s+.*$""") "'''" + x + "'''" else x).mkString(" ")) match {
      case Success((flags, strings), _) => 
        val rem = ListBuffer[~[String, List[String]]]();
        for (f <- flags) f match {
          // parse config/help options first (this allows other command-line settings to override)
          case ("C" | "config" | "configuration" | "cfg") ~ values => 
            assert(values.length >= 1, "No Configuration file specified");
            for (v <- values) config.readSettings(v);
          case ("help" | "h" | "?") ~ _ => usage(); System.exit(1); //throw ExitException("help");
          case x @ _ => rem += x; // passthrough
        }
        for (f <- rem) f match {
          case arg ~ Nil => checkedSet(arg, List("true")); // flag argument
          case arg ~ value => checkedSet(arg, value); //value.mkString(" "));
        }
        config.print(out);
        strings.toArray;
      case err @ _ => assert(false, "Error parsing command line: " + err); null;
    }
  }
}

/**
 * ==Command-line/config tester== */
// -------------------------------------------------------------------------------------------------------------------------------------
object ArgTest extends ArgHandler {
  val program = "test";
  var s = "test";
  var f = 1.0f;
  var i = 1;
  var b = false;
  var set = Set("1", "2");
  var lt  = List("Test" -> "test", "test" -> "another");
  // register arguments directly
  config += "General" -> 
    Params("string" -> Arg(s _, s_= _,     "string argument"),
           "float"  -> Arg(f _, f_= _,     "float argument"),
           "int"    -> Arg(i _, i_= _,     "int argument"),
           "set"    -> Arg(set _, set_= _, "set argument"),
           "tuples" -> Arg(lt _, lt_= _,   "list of tuples argument"),
           "bool"   -> Arg(b _, b_= _,     "boolean flag argument"));
  config += "Major" -> 
    Params("string" -> Arg("test2",          "string argument"),
           "float"  -> Arg(2.0f,             "float argument"),
           "int"    -> Arg(2,                "int argument"),
           "set"    -> Arg(Set("2"),         "set argument"),
           "tuples" -> Arg(List("x" -> "y"), "list of tuples argument"),
           "bool"   -> Arg(false,            "boolean flag argument"));

  def main(args : Array[String]) {
    parseArgs(args);
    // now do stuff
  }
}
