/*
 *  Written by Wade Shen <swade@ll.mit.edu>
 *  Copyright 2005-2016 Massachusetts Institute of Technology, Lincoln Laboratory
 *  Revision: 0.2
 */
package mitll.lid

import java.lang.management._
import java.sql.{Connection, DriverManager}
import java.text.SimpleDateFormat
import java.util.Date

import mitll.lid.utilities._
import org.apache.commons.io.filefilter.WildcardFileFilter

import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.io.Source


// wrapper for a persistent checkpoint file/db
case class CheckPointSet(ckptfn : String) {
  var db : Connection = null
  try {
    Class.forName("org.h2.Driver")
    db = DriverManager.getConnection("jdbc:h2:" + ckptfn, "ckpts", "ckpts")
    val stat = db.prepareStatement("CREATE TABLE if not exists checkpoints (name VARCHAR, index INT, time TIMESTAMP default current_timestamp(), CONSTRAINT pk PRIMARY KEY (name, index));")
    stat.execute;
  } catch { case e : Throwable => e.printStackTrace(); System.exit(1); }

  def apply(name : String, index : Int) = {
    val stat = db.prepareStatement("SELECT * FROM checkpoints WHERE name = ? and index = ?;"); stat.setString(1, name); stat.setInt(2, index)
    val rs = stat.executeQuery
    if (rs.next()) rs.getDate(3) : Date
    else null
  }
  def update(name : String, index : Int, date : Date) {
    val stat = db.prepareStatement("MERGE INTO checkpoints VALUES(?, ?, ?);"); stat.setString(1, name); stat.setInt(2, index); stat.setTimestamp(3, new java.sql.Timestamp(date.getTime))
    val rs = stat.executeUpdate
  }
  def close = db.close

  def retrieve(dir : String) {
    for (f <- dir.list(new WildcardFileFilter("ckpt.*")))
      f match {
        case CheckPointSet.CJPattern(name) =>
          for (step <- FileLines(dir / f)) {
            val CheckPointSet.CheckPointStep(index, date) = step.trim
            this(name, index.toInt) = CheckPointSet.df.parse(date)
          }
          (dir / f).delete;
        case _ => assert(false, "Illegally formatted checkpoint!");
      }
  }
}
object CheckPointSet { 
  val CJPattern      = """^ckpt\.(.*)$""".r
  val CheckPointStep = """^(\d+)\s+(.*)$""".r
  val df             = new SimpleDateFormat("EEE MMM d HH:mm:ss z yyyy")
}

case class Command(val value : String) {
  def echo(prefix : String) = prefix + "echo '" + shellQuote(value) + "'"

  def toSH(prefix : String) = prefix + shellEscape(value)

  def toSH : String = toSH("")

  def echoMake(prefix : String) = prefix + "echo '" + makeEscape(shellQuote(value)) + "'"

  def toMake(prefix : String) = prefix + makeEscape(value)

  def toMake : String = toMake("\t")

  // NOTE: $ appears in class names, and thus file names. These are quoted.  Literal $ is #d#
  def shellEscape(st : String) = if (platform == "win32") st else st.replaceAll("""\$""", """\\\$""").replaceAll("#d#", """\$""")

  def makeEscape(st : String) = st.replaceAll("#d#", """\$""").replaceAll("""\$""", """\\\$\$"""); // retain $ for make
  def shellQuote(st : String) = st.replace("'", "\"")

  def run(log : Log) : Int = {
    val winp = if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) true else false
    val a = if (winp) Array("cmd", "/c", shellEscape(value)) else Array("sh", "-c", shellEscape(value))
    val p = new ProcessBuilder(a : _*)
    p.redirectErrorStream(true)

    log("LOCAL", "Running: %s\n", value)
    val proc = p.start()
    for (line <- Source.fromInputStream(proc.getInputStream(), "UTF8").getLines) log("JOB", line)

    proc.waitFor()
    proc.exitValue()
  }
}

class Job(val name : String, commands : Array[Command]) {
  def length = commands.length

  def apply(idx : Int) = commands(idx)

  def toSH : Array[String] = commands.map(_.toSH)

  def toMake : Array[String] = commands.map(_.toMake)

  def toShellScript(fn : String) {
    withPrint(fn) { f => 
      f.print(scpHeader)
      for (cmd <- commands) f.print(cmd.toSH);
    }
  }
  def run(log : Log) : Int = commands.foldLeft(0) { case (prev, cmd) => prev | cmd.run(log); }
  def filter(f: (String, Int) => Boolean) : Array[(Command, Int)] = commands.zipWithIndex.filter { case (cmd, i) => f(name, i) }
}
case class QuickJob(n : String, cmds : String*) extends Job(n, cmds.map(Command(_)).toArray)

case class LongJob(n : String, cmds : String*) extends Job(n, cmds.map(Command(_)).toArray)

class JobQ(val workdir : String, val ckpts : CheckPointSet, var log : Log, val mode : Arg[String], val attr : Arg[String], checkpoint : Arg[Boolean], val jobs : Arg[Int]) {
  workdir.mkdirs()
  val q = ArrayBuffer[Job]()

  def checkpoint(name : String, step : Int)(block : => Unit) { if(ckpts(name, step) == null) {block; ckpts(name, step) = new Date();} }
  def add(name : String, steps : String*) { q+= LongJob(name, steps : _*); }
  def add(j : Job) {
    j match {
      case LongJob(_, _) => q += j;
      case QuickJob(_, _) => val status = j.run(log); if (status != 0) fail(status);
    }
  }

  def run {
    val status = mode() match { 
      case JobQ.QPattern(m, q) => runSGELSF(q);
      case "pmake" => runMake(true);
      case "make" => runMake(false);
      case _ => runLocal;
    }
    q.clear
    ckpts.retrieve(workdir)
    if (status != 0) fail(status)
  }
  def runNow(name : String, steps : String*) { val status = QuickJob(name, steps : _*).run(log); if (status != 0) fail(status); }
  def length : Int = q.length

  // Utilities
  def needsRun(name : String, step : Int) : Boolean = if (!checkpoint() || ckpts(name, step) == null) true else false

  def printCompleted =
    for (j <- q; i <- 0 until j.length; if (ckpts(j.name, i) != null))
      log("COMPLETE", "%-60s %10s already done [%s].\n", j.name, "[Step: " + i + "]", JobQ.odf.format(ckpts(j.name, i)))

  def runLocal : Int = {
    val winp = if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) true else false
    printCompleted
    for (j <- q; (cmd, idx) <- j.filter(needsRun _)) {
      val status = cmd.run(log)
      if (status != 0) return status
      if (checkpoint()) ckpts(j.name, idx) = new Date()
    }
    0
  }
  def runMake(pmake : Boolean) : Int = {
    printCompleted
    if (q.length == 0) return 0
    val runner = workdir / "Makefile"
    var i = 0
    var tgts = ListBuffer[String]()
    withPrint(runner) { f =>
      if (pmake) f.println(".EXPORT: \n.EXPORT: " + attr)

      for (j <- q) {
        val cmds = j.filter(needsRun _)
        if (cmds.length > 0) {
          var first = true
          f.println(j.name + ":")
          for ((cmd, step) <- cmds) { 
            f.println(cmd.echoMake("\t"))
            f.println(cmd.toMake)
            if (checkpoint()) { 
              val ccommand = Command("echo " + step + " `date` " + (if (first) ">" else ">>") + " " + workdir / "ckpt." + j.name)
              f.println(ccommand.toMake)
              first = false
            }
            i += 1
          }
          tgts += j.name
        }
      }
      f.println(tgts.mkString("all: ", " ", ""));
    }
    if (i > 0) {
      val ret = run("MAKE", (if (pmake) "pmake -s -J " else "make -s -j ") + jobs + " -f " + runner + " all")
      runner.delete()
      return ret
    }
    0
  }
  def runSGELSF(queue : String) : Int = {
    printCompleted
    if (q.length == 0) return 0
    def jname = "j-" + ManagementFactory.getRuntimeMXBean().getName().replaceFirst("""\@.*$""", "")
    val runner = workdir / "array.sh"
    var i = 1
    withPrint(runner) { f =>
      f.print(scpHeader)
      for (j <- q) {
        val cmds = j.filter(needsRun _)
        if (cmds.length > 0) {
          var first = true
          f.println("if [ \"$SGE_TASK_ID\" = \"" + i + "\" ]; then")
          for ((cmd, step) <- cmds) { 
            f.println(cmd.echo("\t"))
            f.println(cmd.toSH("\t"))
            if (checkpoint()) { f.println("\techo " + step + " `date` " + (if (first) ">" else ">>") + " " + workdir / "ckpt." + j.name); first = false; }
          }
          f.println("fi")
          i += 1
        }
      }
    }
    runner.setExecutable(true); // 1.6 specific                                                                                                                                                                                                                                                           
    val ret = if (i > 1) run("SGE", "qsub -V -q " + queue + " " + attr + " -t 1:" + (i - 1).toString + " -sync yes -b yes -j yes -N " + jname + " -cwd -o " + workdir + " " + runner)
      else 0
    for (j <- 1 until i) {
      val name = q(j-1).name
      val fns = workdir.list(new WildcardFileFilter(jname + ".o*." + j)); // work around sge sprintf bug
      if (fns.length == 1) {
        val fn = workdir / fns(0)
        log(name, "------------- Output for %s -------------", name)
        for (line <- FileLines(fn)) log("OUT-" + name, line)
        fn.delete()
      }
      else log("WARN", "------------- MISSING output for %s -------------", name)
    }
    ret
  }
  def run(name : String, fn : String) : Int = run(name, fn.split("""\s+"""))

  def run(name : String, parts : Array[String]) : Int = run(name, parts, true)

  def run(name : String, parts : Array[String], logp : Boolean) : Int = {
    val p = new ProcessBuilder(parts : _*)
    p.redirectErrorStream(true)

    log(name, "Running: %s\n", parts.mkString(" "))
    val proc = p.start()
    val bufferedSource = Source.fromInputStream(proc.getInputStream(), "UTF8")
    for (line <- bufferedSource.getLines) if (logp) log(name, line); else println(line)

    proc.waitFor()
    bufferedSource.close

    proc.exitValue()
  }

  def fail(status : Int) {
    log("ERROR", "Job failed!\n")
    cleanUp("unexpectedly")
    throw Fatal("JobQueue failure: " + status); //System.exit(status);
  }
  def cleanUp(adverb : String) {
    ckpts.retrieve(workdir)
    if (adverb != null) Console.printf("INFO[JobQueue]: Finished " + adverb + ".\n")
  }
}
object JobQ {
  val QPattern  = """^(sge|lsf)-(.*)$""".r
  val odf       = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
}

trait JobQFactory extends ArgHandler {
  val log : Log
  implicit val tmpdir = Arg("./tmp",     "Directory for storing temporary files")
  val mode            = Arg("sge-all.q", "Parallel job dispatch mode [sge, lsf, pmake, or local]")
  val jobs            = Arg(1,           "Number of jobs to dispatch simultaneously")
  val attr            = Arg("",          "Job submission attributes")
  val checkpoint      = Arg(true,        "Checkpoint jobs")
  val instance        = Arg(".ckpts",    "Instance name for the checkpoints file. The ckpts filename is constructed as `instance.program`")
  config += "Job Control Options" -> Params("mode" -> mode, "jobs" -> jobs, "tmpdir" -> tmpdir, "attr" -> attr, "checkpoint" -> checkpoint, "instance" -> instance)

  lazy val ckptfn   = instance() + "." + program
  lazy val runLock  = tmpdir / ".running." + program
  lazy val ckpts    = CheckPointSet(ckptfn)
  var factoryIsOpen = false
  val submitQs      = ArrayBuffer[JobQ]()

  // JobQ factory
  def JobQ(workdir : String, log : Log = log, mode : Arg[String] = mode, attr : Arg[String] = attr, checkpoint : Arg[Boolean] = checkpoint, jobs : Arg[Int] = jobs) = {
    if (!factoryIsOpen) setupQFactory
    val ret = new JobQ(workdir, ckpts, log, mode, attr, checkpoint, jobs)
    submitQs += ret
    ret
  }
  implicit lazy val JobQMaker = (workdir : String) => JobQ(workdir); // expose the simple version to internal clients

  def setupQFactory {
    assert(tmpdir().existe && tmpdir().isDirectory, sprintf("tmpdir(%s) doesn't exist!", tmpdir))
    if (runLock.existe) {
      log("FATAL", "A cjob or JobQueue script/program may be running")
      log("FATAL", "or may have died unexpectedly, last run:")
      for (l <- FileLines(runLock)) log("FATAL", " + " + l)
      throw Fatal("Run lock: " + runLock + " already exists!")
    }
    else {
      withPrint(runLock)(_.printf("Process %s started at %s\n", ManagementFactory.getRuntimeMXBean().getName(), new Date()))
      runLock.deleteOnExit()
      Runtime.getRuntime().addShutdownHook(new Thread { 
        override def run() { 
          if (log != null && runLock.existe) {
            submitQs.map(_.cleanUp(null))
            shutdownQFactory
          }
        }  
      })
    }
    factoryIsOpen = true
  }
  def shutdownQFactory { runLock.delete; ckpts.close; }
}
trait JobQEntryPoint[T] extends ArgHandler with JobQFactory { outer =>
  val logfile      = Arg("",          "Optional log file name (\"\" == STDERR)")
  config.sections("Job Control Options") += "log" -> logfile
  lazy val log     = new Log(if (logfile() == "") null else logfile())

  // All entry points (programatic callers and command line entry points) should define these:
  def run(args : Array[String]) : T

  def ep(args : Array[String]) : T = {
    try { 
      //setupQFactory;
      run(parseArgs(args, log))
    }
    catch { case e : Throwable => throw e; } //throw Fatal("Error occurred in main program: " + e.getMessage); }
    //finally { shutdownQFactory; }
  }
}

