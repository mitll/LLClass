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
import java.io.{File, FileOutputStream, PrintStream}

import mitll.lid.utilities._

object convert extends ArgHandler {
  val program = "org.convert"
  val input  = Arg("",  "Input Org-mode file to convert")
  val output = Arg("-", "Output file (- = stdout)")
  config += "Org Converter" -> Params("in" -> input, "out" -> output)

  def main(args : Array[String])
  {
    parseArgs(args)
    val (f, o) = (new File(input), new File(output))
    assert(f.exists && f.canRead, "ERROR: Unable to open " + input + " for reading")
    assert(output == "-" || !o.exists || o.canWrite, "ERROR: Unable to open " + output + " for writing")
    val outf = if (output == "-") System.out else new PrintStream(new FileOutputStream(output), true, "UTF8")
    outf.println("{{>toc}}")
    outf.println()
    for (l <- org2textile(input)) outf.println(l)
  }
}
