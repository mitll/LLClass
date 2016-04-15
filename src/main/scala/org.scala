/**
 * org.scala
 *
 * @author Wade Shen <BR>
 * <tt><a href=mailto:swade@ll.mit.edu>swade@ll.mit.edu</a></tt> <BR>
 * Copyright &copy; 2005 Massachusetts Institute of Technology, Lincoln Laboratory
 * @version 0.1a
 */

package mitll
import utilities._
import scala.io._;
import java.io.{File, FileOutputStream, PrintStream};
import scala.collection.Map;
import scala.collection.mutable.{LinkedHashMap, HashMap, ArrayBuffer};

object convert extends ArgHandler {
  val program = "org.convert";
  val input  = Arg("",  "Input Org-mode file to convert");
  val output = Arg("-", "Output file (- = stdout)");
  config += "Org Converter" -> Params("in" -> input, "out" -> output);

  def main(args : Array[String])
  {
    parseArgs(args);
    val (f, o) = (new File(input), new File(output)); 
    assert(f.exists && f.canRead, "ERROR: Unable to open " + input + " for reading");
    assert(output == "-" || !o.exists || o.canWrite, "ERROR: Unable to open " + output + " for writing");
    val outf = if (output == "-") System.out else new PrintStream(new FileOutputStream(output), true, "UTF8");
    outf.println("{{>toc}}");
    outf.println();
    for (l <- org2textile(input)) outf.println(l);
  }
}
