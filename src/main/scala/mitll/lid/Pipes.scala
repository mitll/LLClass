/*
 *  Written by Wade Shen <swade@ll.mit.edu>
 *  Copyright 2005-2016 Massachusetts Institute of Technology, Lincoln Laboratory
 *  Revision: 0.2
 */

package mitll
import java.io.{BufferedOutputStream, FileOutputStream, PrintStream}
import java.lang.reflect.{Field, Method}

import mitll.utilities._

import scala.collection.Iterable
import scala.collection.immutable.Vector
import scala.collection.mutable.{ArrayBuffer, HashMap, Map}
import scala.io.Source
import scala.util.parsing.combinator.RegexParsers

// -------------------------------------------------------------------------------------------------------------------------------------
// Processor and friends
// -------------------------------------------------------------------------------------------------------------------------------------
abstract class Processor { outer =>
  implicit def arg2T[T](a : Arg[T]) : T = a.apply()

  // Reflect settable fields
  def registerArgs : Params[Arg[_]] = {
    def method2f0[T](m : Method) = new Function0[T] { def apply() : T = m.invoke(outer).asInstanceOf[T]; }
    def method2f1[T](m : Method) = new Function1[T, Unit] { def apply(i : T) { m.invoke(outer, i.asInstanceOf[Object]); } }

    def registerField(f: Field){
      // log("DEBUG", "Field: " + f.getName + " = " + f.getAnnotations.mkString("(", ", ", ")")); 
      // f.isAnnotationPresent(classOf[arg]));
//      if (f.isAnnotationPresent(classOf[arg])) {
//        val anno = f.getAnnotation(classOf[arg]).asInstanceOf[arg];
//        val name = if (anno.name == "") f.getName else anno.name;
//	val get = method2f0(try { this.getClass.getMethod(f.getName) } catch { case _ => throw Fatal("Can't find an appropriate setter for: " + f.getName + ", in class: " + name); });
//        val set = method2f1(try { this.getClass.getMethod(f.getName + "_$eq", f.getType) } catch { case _ => throw Fatal("Can't find an appropriate setter for: " + f.getName + ", in class: " + name); });
//        params(name) = f.getType.getName match {
//          case "java.lang.String"  => Arg[String](getter = get, setter = set, help = anno.help);
//          case "int"               => Arg[Int](getter = get, setter = set, help = anno.help);
//          case "float"             => Arg[Float](getter = get, setter = set, help = anno.help);
//          case "boolean"           => Arg[Boolean](getter = get, setter = set, help = anno.help);
//          case "scala.collection.immutable.Set$Set1"      => Arg[Set[String]](getter = get, setter = set, help = anno.help);
//          case "scala.collection.immutable.Set$EmptySet$" => Arg[Set[String]](getter = get, setter = set, help = anno.help);
//          case "scala.collection.immutable.$colon$colon"  => Arg[List[(String, String)]](getter = get, setter = set, help = anno.help);
//          case "scala.collection.immutable.Nil$"          => Arg[List[(String, String)]](getter = get, setter = set, help = anno.help);
//          case x @ _ => throw Fatal("argument type: " + x + " is unsupported");
//        }
//    }
    }

    var c = getClass()
    for(field <- c.getDeclaredFields()) registerField(field)

    while(c.getSuperclass() != null){
      var a = c.getSuperclass()
      for(field <- a.getDeclaredFields()) registerField(field)
    }

    params
  }

  // Utility functions
  def setInstanceValues(p : Map[String, String]) { 
    for ((k, v) <- p) {
      assert(params isDefinedAt k, "Processor " + name + " doesn't allow setting (" + k + ")!\n" + "Possible Settings: " + params.keys.mkString(", "))
      params(k) << p(k)
    }
  }
  def setParams(p : Map[String, Arg[_]]) { params.keys.foreach { k => if (p isDefinedAt k) params(k) << p(k).toString; } }
  def getParams = (name, params)

  def defaultMap : Map[String, (String, (Arg[_]) => Any)] = params.defaultMap

  // Utility functions
  def dir() : String = tmpdir / name + "-" + idx

  def fn(base : String) : String = dir / base

  def getStream(name : String, tofn : (String) => String = fn _) = new FileOutputStream(tofn(name))

  def withStream[T](name : String, tofn : (String) => String = fn _)(block : (FileOutputStream) => T) : T = {
    val f = getStream(name, tofn); try block(f) finally f.close
  }
  def getPrintStream(base : String, enc : String = "UTF8", tofn : (String) => String = fn _, afl : Boolean = false) = 
    new PrintStream(new BufferedOutputStream(getStream(base, tofn)), afl, enc)

  def withPrintStream[T](name : String, enc : String = "UTF8", tofn : (String) => String = fn _)(block : (PrintStream) => T) : T = {
    val f = getPrintStream(name, enc, tofn); try block(f) finally f.close
  }
  def makeINI(name : String, map : Map[String, (String, (Arg[_]) => Any)]) = params.print(fn(name), map, "ini")

  def makeParams(name : String, map : Map[String, (String, (Arg[_]) => Any)]) = params.print(fn(name), map, "params")

  def makeCFG(name : String, map : Map[String, (String, (Arg[_]) => Any)]) = params.print(fn(name), map, "cfg")

  def exe(fn : String) : String = params("base-directory") / "bin." + params("arch") / fn

  def script(fn : String) : String = params("base-directory") / "scripts" / fn

  def jobName() = name + "-" + idx

  override def toString = name + "[" + params + "]"

  // Data
  val tmpdir : Arg[String];   // This is global/context dependent : each processor keeps a reference
  val log    : Log
  var name                    = this.getClass.getName
  val params : Params[Arg[_]] = new Params
  val idx                     = Processor.index(name); Processor.index(name) += 1

  // Shared parameter values
  //params("arch") = Arg(platform, "Architecture for processing binaries")
  //params("base-directory") = Arg(".", "Base directory containing binaries and scripts dirs")
//  @arg(help = "Architecture for processing binaries")                                       var arch    = platform;
//  @arg(name = "base-directory", help = "Base directory containing binaries and scripts dirs") var baseDir = ".";


}
object Processor { val index = new HashMap[String, Int]() { override def default(k : String) = 0; }; }

/**
 * == Base trait for all Parallel Processes ==
 * These processors compute data and metadata inputs and outputs in two phases `startM` and `collectorM`. Processors are instantiated and executed by Pipes (see [[Utils.Pipe]] for details).
 * It is assumed that `startM` computation can be parallelized per input set (of type `Iterable[I]`) and that synchronization is required prior to collection. Collection
 * can happen either in parallel or sequentially.
 *
 * Metadata is defined as shared data across elements of the input set. In many cases metadata is global (i.e. the same object across sets in an "==" sense).
 * In these cases (currently), it is up to the process writer to maintain consistency across input sets.
 *
 * Subclasses exist for processors that:
 * 1. don't care about metadata and there default behavior is simply to pass on the metadata (see [[Utils.ParallelProcess]])
 * 2. processes that need all inputs during collection (see [[Utils.Combiner]] and [[Utils.CombinerM]])
 * 3. processes that are exclusively data parallel and don't require synchronization between stages (see [[Utils.ComposableMio]])
 * 
 * @tparam I  Type of data input elements
 * @tparam O  Type of data output elements
 * @tparam Mi Type of metadata input elements
 * @tparam Mo Type of metadata output elements */
trait ParallelProcessMio[I, Mi, O, Mo] extends Processor { orig =>
  def dir(inputIdx : Int) : String = dir() / inputIdx

  def fn(inputIdx : Int, base : String) : String = dir(inputIdx) / base

  def forwardFn(inputIdx : Int, base : String) : String = dir(inputIdx) + "/" + base; // SPECIAL: need explicit forward slash for some cygwin compile binaries
  def jobName(inputIdx : Int) : String = name + "-" + idx + "-set-" + inputIdx

  def command(inputIdx : Int, bin : String, files : String*) : String = (exe(bin) :: files.map(fn(inputIdx, _)).toList).mkString(" ")

  def forwardSlashCommand(inputIdx : Int, bin : String, files : String*) : String = (exe(bin) :: files.map(forwardFn(inputIdx, _)).toList).mkString(" ")

  def scriptCmd(inputIdx : Int, bin : String, files : String*) : String = (script(bin) :: files.map(fn(inputIdx, _)).toList).mkString(" ")

  def getStream(inputIdx : Int, name : String) : FileOutputStream = getStream(name = name, tofn = fn(inputIdx, _))

  def withStream[T](inputIdx : Int, name : String)(block : (FileOutputStream) => T) : T = withStream(name, fn(inputIdx, _))(block)

  def getPrintStream(inputIdx : Int, name : String, enc : String) : PrintStream = getPrintStream(name, enc, fn(inputIdx, _))

  def getPrintStream(inputIdx : Int, name : String) : PrintStream = getPrintStream(name, "UTF8", fn(inputIdx, _))

  def withPrintStream[T](inputIdx : Int, name : String, enc : String)(block : (PrintStream) => T) : T = withPrintStream(name, enc, fn(inputIdx, _))(block)

  def withPrintStream[T](inputIdx : Int, name : String)(block : (PrintStream) => T) : T = withPrintStream(name, "UTF8", fn(inputIdx, _))(block)

  def makeINI(inputIdx : Int, name : String, map : Map[String, (String, (Arg[_]) => Any)]) = params.print(fn(inputIdx, name), map, "ini")

  def makeParams(inputIdx : Int, name : String, map : Map[String, (String, (Arg[_]) => Any)]) = params.print(fn(inputIdx, name), map, "params")

  def makeCFG(inputIdx : Int, name : String, map : Map[String, (String, (Arg[_]) => Any)]) = params.print(fn(inputIdx, name), map, "cfg")

  // API functions
  def startM(q : JobQ, in : Iterable[I], inputIdx : Int, meta : Mi)

  def collectorM(q : JobQ, in : Iterable[I], inputIdx : Int, meta : Mi) : Vector[(Mo, Iterable[O])]

  def collectM(q : JobQ, aggregate : Vector[(Mi, Iterable[I])], inputIdx : Int) : Vector[(Mo, Iterable[O])] = collectorM(q, aggregate(inputIdx)._2, inputIdx, aggregate(inputIdx)._1)

  // DSL Support: parallel operations: +-operator    -- make two independent parallel processors into a single processor
  def +(other : ParallelProcessMio[I, Mi, O, Mo]) = new ParallelProcessMio[I, Mi, O, Mo] { 
    if (orig.mode != other.mode || orig.attr != other.attr || orig.checkpoint != other.checkpoint || orig.jobs != other.jobs) 
      throw Fatal("Incompatible q settings when trying to combine " + orig.name + " with " + other.name)
    val Qfactory = orig.Qfactory
    val log = orig.log
    val tmpdir = orig.tmpdir
    this.mode = orig.mode
    this.attr = orig.attr
    this.checkpoint = orig.checkpoint
    this.jobs = orig.jobs
   
    override def toString = orig.toString + " + " + other.toString

    def startM(q : JobQ, in : Iterable[I], inputIdx : Int, meta : Mi) { orig.startM(q, in, inputIdx, meta); other.startM(q, in, inputIdx, meta); }
    def collectorM(q : JobQ, in : Iterable[I], inputIdx : Int, meta : Mi) : Vector[(Mo, Iterable[O])] = throw Fatal("Don't call this!")

    override def collectM(q : JobQ, aggregate : Vector[(Mi, Iterable[I])], inputIdx : Int) : Vector[(Mo, Iterable[O])] = orig.collectM(q, aggregate, inputIdx) ++ other.collectM(q, aggregate, inputIdx)
  }

  // JobQ-specific settings
  val Qfactory     : String => JobQ
  lazy val q       = Qfactory(dir() / "cjob")
  var mode       = "sge-all.q"
  var attr       = ""
  var checkpoint = true
  var jobs       = 1
}
trait ParallelProcessM[I, M, O] extends ParallelProcessMio[I, M, O, M]

trait ParallelProcess[I, O] extends ParallelProcessM[I, Any, O] {
  /** Start a parallel process on the JobQ, taking in a sequence of I objects. Disambiguate using the inputIdx. */
  def start(q : JobQ, in : Iterable[I], inputIdx : Int)

  def collector(q : JobQ, in : Iterable[I], inputIdx : Int) : Vector[Iterable[O]]

  /** Collect the results produced by start(), producing a list of Iterables. Each Iterable's elements corresponds to an input element. ??? */
  def collect(q : JobQ, aggregate : Vector[Iterable[I]], inputIdx : Int) = collector(q, aggregate(inputIdx), inputIdx)

  // by default parallel processes don't use metadata, just forward
  def startM(q : JobQ, in : Iterable[I], inputIdx : Int, meta : Any) { start(q, in, inputIdx); }
  def collectorM(q : JobQ, in : Iterable[I], inputIdx : Int, meta : Any) : Vector[(Any, Iterable[O])] = null

  override def collectM(q : JobQ, aggregate : Vector[(Any, Iterable[I])], inputIdx : Int) : Vector[(Any, Iterable[O])] = {
    val r = collect(q, aggregate.map(_._2), inputIdx)
    val m = aggregate(inputIdx)._1
    if (r != null) r.map(d => m -> d) else null
  }
}

trait ComposableMio[I, Mi, O, Mo] extends Processor { self =>
  // Utilities
  def runProcess(command : String) : Int = {
    val a : Array[String] = command.split("""\s+""")
    val p = new ProcessBuilder(a.toArray : _*)
    p.redirectErrorStream(true)

    val proc = p.start()
    val bufferedSource = Source.fromInputStream(proc.getInputStream())
    for (line <- bufferedSource.getLines) print(line)

    proc.waitFor()
    bufferedSource.close
    proc.exitValue()
  }

  // API Functions
  def startup = true

  def shutdown = true

  def doM(input : Mi) : Mo

  def doitM(input : I, meta : Mi) : O

  // DSL
  def *[fO, fMo](f : ComposableMio[O, Mo, fO, fMo]) = new ComposableMio[I, Mi, fO, fMo] {
    val log = self.log; val tmpdir = self.tmpdir

    override def toString = self.toString + " * " + f.toString

    def doitM(input : I, meta : Mi) = f.doitM(self.doitM(input, meta), self.doM(meta))

    def doM(meta : Mi) = f.doM(self.doM(meta))
  }
  var defaultMi : Mi = _

  def on(inputs : Iterable[I], meta : Mi) : (Mo, Iterable[O]) = doM(meta) -> inputs.map(x => doitM(x, meta))

  def apply(inputs : Iterable[I], meta : Mi) = on(inputs, meta)

  def |>:(inputM : (Iterable[I], Mi) ) = on(inputM._1, inputM._2)

  def on(inputs : Iterable[I]) : Iterable[O] = on(inputs, defaultMi)._2

  def apply(inputs : Iterable[I]) : Iterable[O] = on(inputs, defaultMi)._2

  def |>:(inputs : Iterable[I]) = on(inputs, defaultMi)._2

  def apply(input : I, meta : Mi) = doitM(input, meta)

  def apply1(input : I) = doitM(input, defaultMi)

  def on1(input : I) = doitM(input, defaultMi)
}
trait ComposableM[I, M, O] extends ComposableMio[I, M, O, M] { def doM(input : M) : M = input; }
trait Composable[I, O] extends ComposableM[I, Any, O] {
  def doitM(input : I, meta : Any) = doit(input)

  /** Take a single input and produce a single corresponding output. */
  def doit(input : I) : O
}

/** Aggregate Iterables of type T. Eventually produce an output of type W. */
abstract class GeneralAggregator[T, W] extends ParallelProcess[T, W] { 
  def start(q : JobQ, in : Iterable[T], inputIdx : Int) { } 
  def combine(q : JobQ, aggregate : Vector[Iterable[T]]) : Vector[Iterable[W]]

  override def collect(q : JobQ, aggregate : Vector[Iterable[T]], ii : Int) : Vector[Iterable[W]] =
    if (ii == 0) combine(q, aggregate) else Vector[Iterable[W]]()

  def collector(q : JobQ, in : Iterable[T], inputIdx : Int) : Vector[Iterable[W]] = null

} 

/** Aggregate Iterables of type T, saving metadata M as well. Eventually produce an output of type W. */
abstract class GeneralAggregatorMio[T, Mi, W, Mo] extends ParallelProcessMio[T, Mi, W, Mo] { 
  def startM(q : JobQ, in : Iterable[T], inputIdx : Int, meta : Mi) { } 
  def combineM(q : JobQ, aggregate : Vector[(Mi, Iterable[T])]) : Vector[(Mo, Iterable[W])]

  override def collectM(q : JobQ, aggregate : Vector[(Mi, Iterable[T])], ii : Int) : Vector[(Mo, Iterable[W])] =
    if (ii == 0) combineM(q, aggregate) else Vector[(Mo, Iterable[W])]()

  def collectorM(q : JobQ, in : Iterable[T], inputIdx : Int, meta : Mi) : Vector[(Mo, Iterable[W])] = null
}
abstract class GeneralAggregatorM[T, M, W] extends GeneralAggregatorMio[T, M, W, M]

/** A Combiner aggregates Iterables of type T into objects of type T. */
abstract class Combiner[T] extends GeneralAggregator[T, T]

/** A CombinerM aggregates Iterables of type T into objects of type T, with metadata of type M. */
abstract class CombinerM[T, M] extends GeneralAggregatorMio[T, M, T, M]

abstract class CombinerMio[T, Mi, Mo] extends GeneralAggregatorMio[T, Mi, T, Mo]

// -------------------------------------------------------------------------------------------------------------------------------------
// Pipeline
// -------------------------------------------------------------------------------------------------------------------------------------
trait Pipe[I, Mi, O, Mo] {
  var defaultMi : Mi = _

  def ->:[X, Mx](left : Pipe[X, Mx, I, Mi]) = PipeLine(left, this)

  def ->:(left : Vector[Iterable[I]]) = apply(left)

  def ->:(left : (Vector[Mi], Vector[Iterable[I]])) = apply(left._1, left._2)

  def =>:(left : Iterable[I]) = apply1(left)

  def =+>:(left : Iterable[I]) = apply1(defaultMi, left)

  def =>:(left : (Mi, Iterable[I])) = apply1(left._1, left._2)

  def apply1(meta : Mi, in : Iterable[I]) = apply(Vector(meta), Vector(in)).head

  def on1(meta : Mi, in : Iterable[I]) = apply1(meta, in)

  def apply(metas : Vector[Mi], ins : Vector[Iterable[I]]) : Vector[(Mo, Iterable[O])]

  def on(metas : Vector[Mi], ins : Vector[Iterable[I]]) = apply(metas, ins)

  def apply1(in : Iterable[I]) = apply(Vector(in)).head

  def on1(in : Iterable[I]) = apply(Vector(in)).head

  def apply(ins : Vector[Iterable[I]]) : Vector[Iterable[O]] = apply(ins.map(x => defaultMi), ins).map(_._2)

  def on(ins : Vector[Iterable[I]]) = apply(ins)

  def toString : String

  def toListString : List[String]
}

case class EndPipe[I, Mi, O, Mo](proc : ParallelProcessMio[I, Mi, O, Mo]) extends Pipe[I, Mi, O, Mo] { 
  def apply(metas : Vector[Mi], ins : Vector[Iterable[I]]) = { 
    for (((meta, part), idx) <- (metas zip ins).zipWithIndex) proc.startM(proc.q, part, idx, meta)
    if (proc.q != null) proc.q.run
    var ret = Vector[(Mo, Iterable[O])]()

    for (idx <- 0 until ins.length) {
      val res = proc.collectM(proc.q, metas zip ins, idx)

      if(res != null) {
        ret ++= res
      }
    }
    ret
  }
  override def toString = proc.toString

  def toListString = List(toString)
}

case class PipeLine[I, Mi, X, Mx, O, Mo](proc : Pipe[I, Mi, X, Mx], rest : Pipe[X, Mx, O, Mo]) extends Pipe[I, Mi, O, Mo] {
  def apply(metas : Vector[Mi], ins : Vector[Iterable[I]]) = { 
    val intermediate = proc.apply(metas, ins)
    rest.apply(intermediate.map(_._1), intermediate.map(_._2))
  }
  override def toString = proc.toString + " -> " + rest.toString

  def toListString = proc.toListString ++ rest.toListString
}

case class ComposablePipe[I, Mi, O, Mo](f : ComposableMio[I, Mi, O, Mo]) extends Pipe[I, Mi, O, Mo] {
  def apply(metas : Vector[Mi], ins : Vector[Iterable[I]]) = (metas zip ins).map(x => Tuple2(f.doM(x._1), x._2.view.map((y: I) => f.doitM(y, x._1)).filter(_ != null)))

  override def toString = f.name

  def toListString = List(f.toString)
}

/** DSL Support implicits */
trait IPSLowPriority extends JobQFactory { outer => 
  var logfn : String = null
  implicit lazy val log = new Log(logfn)

  implicit def liftP[I, O](f : Function1[I, O]) = new ParallelProcessMio[I, Null, O, Null] {
    val log = outer.log; val tmpdir = outer.tmpdir; val Qfactory = JobQMaker
    override lazy val q = null

    def startM(q : JobQ, in : Iterable[I], inputIdx : Int, meta : Null) {}
    def collectorM(q : JobQ, ins : Iterable[I], inputIdx : Int, meta : Null) = Vector(Tuple2(meta, ins.map(f)))
  }
  // composable operations: lift-operator -- make simple functions composable (like liftC)
  implicit def liftC[I, O](f : Function1[I, O]) = new Composable[I, O] { val log = outer.log; val tmpdir = outer.tmpdir; def doit(input : I) = f(input); }

  // pipe glue
  implicit def toPipeLine[I, Mi, O, Mo](proc : ParallelProcessMio[I, Mi, O, Mo]) = EndPipe(proc)

  implicit def F1toPipeLine[I, O](f : Function1[I, O]) = EndPipe(liftP(f))
}
trait InternalPipeSupport extends IPSLowPriority with JobQFactory { outer =>

  // implicit def liftCM[I, Mi, O, Mo](f : (Function2[I, Mi, O], Function1[Mi, Mo])) = new ComposableMio[I, Mi, O, Mo] { 
  //   val log = outer.log; val tmpdir = outer.tmpdir; 
  //   def doitM(input : I) = f._2(input); 
  //   def doM(input : I) = f._1(input); 
  // }

  // parallel operations: lift-operator -- make function parallel (like liftC)
  implicit def liftIterable2P[I, Mi, O, Mo](f : Function2[Mi, Iterable[I], (Mo, Iterable[O])]) = new GeneralAggregatorMio[I, Mi, O, Mo] { 
    val log = outer.log; val tmpdir = outer.tmpdir; val Qfactory = JobQMaker
    override lazy val q = null

    def combineM(q : JobQ, aggregate : Vector[(Mi, Iterable[I])]) = aggregate.map { case (m, in) => f(m, in) }
  }
  implicit def lift1Iterable2P[I, O, Mo](f : Function1[Iterable[I], (Mo, Iterable[O])]) = new GeneralAggregatorMio[I, Null, O, Mo] { 
    val log = outer.log; val tmpdir = outer.tmpdir; val Qfactory = JobQMaker
    override lazy val q = null

    def combineM(q : JobQ, aggregate : Vector[(Null, Iterable[I])]) = aggregate.map { case (m, in) => f(in) }
  }

  // implicitly bridge composable to parallelprocessor (at the package or pipesupport level)
  implicit def C2P[I, O](comp : Composable[I, O]) = liftP(comp.doit _)

  // pipe glue
  //implicit def toPipeLine[I, Mi, O, Mo](proc : ParallelProcessMio[I, Mi, O, Mo]) = EndPipe(proc);
  implicit def F1MtoPipeLine[I, O, Mo](f : Function1[Iterable[I], (Mo, Iterable[O])]) = EndPipe(lift1Iterable2P(f))

  implicit def F2toPipeLine[I, Mi, O, Mo](f : Function2[Mi, Iterable[I], (Mo, Iterable[O])]) = EndPipe(liftIterable2P(f))
}

//noinspection ScalaUnnecessaryParentheses,ScalaUnnecessaryParentheses,ScalaUnnecessaryParentheses
trait ProcessorRegistry extends ArgHandler with JobQFactory {
  val registry : Map[String, Class[_]] = HashMap[String, Class[_]]()

  def register(classes : Class[_]*) {
    for (cl <- classes; if !(registry isDefinedAt cl.getName())) {
      registry(cl.getName()) = cl
      val x = instantiateProcessor[Processor](cl.getName(), checkRegistry = false)
      val (name, map) = x.getParams
      assert(!config.sections.isDefinedAt(name))
      config += name -> map
    }
  }
  def instantiateProcessor[T <: Processor](name : String, checkRegistry : Boolean = true) = {
    val cl   = this.getClass.getClassLoader.loadClass(name)
    def getDefaults(cons : java.lang.reflect.Constructor[_], expected : Int) : Array[AnyRef] =  {
      val numargs = cons.getParameterTypes.length
      if (numargs == expected) Array[AnyRef]()
      else if (numargs < expected) throw Fatal("class " + name + " doesn't seem to have a constructor with the expected number of parameters (numargs: " + numargs + ", " + expected + ")")
      else {
        val companionClass = this.getClass.getClassLoader.loadClass(name + "$")
        val cConstructors  = companionClass.getConstructors()
        val ret            = ArrayBuffer[AnyRef]()
        if (cConstructors.length != 0) {
          val companion = cConstructors(0).newInstance()
          for (i <- 1 to (numargs - expected)) { ret += companionClass.getMethod("init$default$" + i).invoke(companion); }
          log("DEBUG", "constructing with the following args: " + ret)
          ret.toArray
        }
        else {
          for (i <- 1 to (numargs - expected)) { ret += cl.getMethod("init$default$" + i).invoke(null); }
          log("DEBUG", "using class without companion: " + name)
          ret.toArray
        }
      }
    }
    val cons = cl.getConstructors()(0)
    val ret  = if (classOf[ParallelProcessMio[_, _, _, _]].isAssignableFrom(cl))  cons.newInstance(getDefaults(cons, 3) ++ Array[AnyRef](log, tmpdir, JobQMaker) : _*).asInstanceOf[T]
    else cons.newInstance(getDefaults(cons, 2) ++ Array[AnyRef](log, tmpdir) : _*).asInstanceOf[T]

    val parms = ret.registerArgs
    if (!registry.isDefinedAt(cl.getName)) {
      log("REGISTER", "Registering " + cl.getName + " with arguments: " + parms.keys.mkString(" "))
      config += cl.getName -> Params(parms.toSeq : _*)
    }
    if (checkRegistry) ret.setParams(config.sections(cl.getName))
    ret
  }
}
//noinspection ScalaUnnecessaryParentheses,ScalaUnnecessaryParentheses,ScalaUnnecessaryParentheses,ScalaUnnecessaryParentheses,ScalaUnnecessaryParentheses,ScalaUnnecessaryParentheses,ScalaUnnecessaryParentheses
trait ExternalPipeSupport extends RegexParsers { self : ProcessorRegistry =>
  val log : Log

  def instantiateProcessor[T <: Processor](name : String, checkRegistry : Boolean = true) : T

  type CP = ComposableMio[Any, Any, Any, Any]
  type P  = ParallelProcessMio[Any, Any, Any, Any]
  type Pi = Pipe[Any, Any, Any, Any]

  // ------------------------------------------- Parser For Pipes -------------------------------------------
  case class PipeInput(data : Int, meta : Int, mOP : String = ":") { override def toString = data + mOP + meta; }

  val str1Regex = """"([^\"]*)"""".r
  val str2Regex = """'([^']*)'""".r

  def ident = """[_a-zA-Z][^\s.(\[\]):*+;=|\"',-]*""".r

  def numericLit = """[+-]?\d+""".r

  def floatLit : Parser[String] = """[+-]?(\d+(\.\d*)?|\d*\.\d+)([eE][+-]?\d+)?[fFdD]?""".r

  def stringLit = str1Regex ^^ { case str1Regex(x) => x } | str2Regex ^^ { case str2Regex(x) => x }; // TODO: Octal / unicode literal parser translation
  def addex : Parser[Pi] = repsep(termparam[P] | terminal[P], "+") ^^ { case a => EndPipe(a.tail.foldLeft(a.head)(_ + _)) }
  def paren : Parser[Pi] = rep1sep(ident, ".") ~ "(" ~ addex ~ ")" ^^ { 
    case a ~ "(" ~ b ~ ")" => PipeLine(b, EndPipe(instantiateProcessor[CombinerMio[Any, Any, Any]](a.mkString("."))));
  }
  def expr : Parser[Pi] = paren | ((termparam[P] | terminal[P]) ^^ { case a => EndPipe(a) })
  def terminal[T <: Processor] : Parser[T] = rep1sep(ident, ".") ^^ { a => instantiateProcessor[T](a.mkString(".")) }
  def setname : Parser[String] = rep1sep(ident, "-" | ".") ^^ { case a => a.mkString("-") }
  def setting : Parser[(String, String)] = setname ~ "=" ~ (ident | floatLit | numericLit | stringLit) ^^ { case a ~ "=" ~ b => (a, b.replaceAll("""\\n""", "\n")) }
  def params : Parser[Map[String, String]] = repsep(setting, ",") ^^ { case a => val ret = new HashMap[String, String](); if (a != null) a.foreach { e => ret(e._1) = e._2; }; ret }
  def termparam[T <: Processor] : Parser[T] = terminal[T] ~ "[" ~ params ~ "]" ^^ { case a ~ "[" ~ b ~ "]" => a.setInstanceValues(b); a }
  def plist : Parser[Pi] = rep1sep(expr, "->") ^^ { case a => a.dropRight(1).foldRight(a.last)(_ ->: _) }
  def pipeSpec : Parser[Pi] = plist | expr

  def inputSpec : Parser[PipeInput] = numericLit ~ (":" | "^") ~ numericLit ^^ { case a ~ op ~ b => PipeInput(a.toInt, b.toInt, op); }
  def sinputSpec : Parser[PipeInput] = numericLit ^^ { case a => PipeInput(a.toInt, a.toInt); }
  def pipe : Parser[(Int, List[PipeInput], Pi)] = numericLit ~ "=" ~ rep1sep(inputSpec | sinputSpec, "+") ~ ":>" ~ pipeSpec ^^ { case a ~ "=" ~ b ~ ":>" ~ c => (a.toInt, b, c) }
  def multex : Parser[CP] = repsep(termparam[CP] | terminal[CP], "*") ^^ { case a => a.tail.foldLeft(a.head)(_ * _) }
  def cpipe : Parser[(Int, List[PipeInput], Pi)] = numericLit ~ "=" ~ rep1sep(inputSpec | sinputSpec, "+") ~ "|>" ~ multex ^^ { case a ~ "=" ~ b ~ "|>" ~ c => (a.toInt, b, ComposablePipe(c)) }
  def pipetype : Parser[(Int, List[PipeInput], Pi)] = pipe | cpipe
  def namelist : Parser[Map[Int, (List[PipeInput], Pi)]] = 
    repsep(pipetype, ";") <~ opt(";") ^^ { case a => { val ret = new HashMap[Int, (List[PipeInput], Pi)]; a.map { l => ret(l._1) = (l._2, l._3); }; ret; } }

  def parse(desc : String) : Map[Int, (List[PipeInput], Pi)] = parseAll(namelist, desc) match {
    case Success(tree, _) => tree;
    case error @ _ => log("ERROR", error.toString); throw Fatal("Parse failure: " + error);
  }
  // ------------------------------------------- End Pipe Parser -------------------------------------------
  def tsort(g : Map[Int, Set[Int]]) : Array[Set[Int]] = {
    var ret = ArrayBuffer[Set[Int]]()
    val uncommitted = collection.mutable.Set[Int](g.keys.toList : _*)
    var committed = new HashMap[Int, Int]() {override def default(key:Int) = -1}
    var delta = 1

    while (uncommitted.size > 0 && delta > 0) {
      delta = 0
      for (i <- uncommitted){
        if (g(i).foldLeft(true)((a, b) => a && !uncommitted(b))) {
	  uncommitted -= i
          delta += 1
          val maxIdx = g(i).map(x => committed(x)).reduceLeft((a, b) => scala.math.max(a, b))
	  committed(i) = maxIdx + 1
	  if(ret.length > maxIdx + 1) ret(maxIdx + 1) += i
	  else ret += Set[Int](i)
        }
      }
    }
    assert(uncommitted.size == 0, "ERROR: cycle in graph involving these nodes: " + uncommitted)
    ret.toArray
  }

  def runPipes(p : Map[Int, (List[PipeInput], Pi)], context : Map[Int, Vector[(Any, Iterable[Any])]]) : Map[Int, Vector[(Any, Iterable[Any])]] = {
    val ret = context
    val plan = tsort(p.map { case (idx, (deps, pipe)) => idx -> Set[Int](deps.flatMap(x => List(x.data, x.meta)) : _*) })

    log("PLAN", "Execution Plan: " + plan.flatMap(_.toList.sortWith(_ < _)).mkString(" -> "))
    for (set <- plan; pid <- set.toList.sortWith(_ < _)) { // TODO: run set members in parallel
      assert(!(ret isDefinedAt pid))
      val (ins, pipe) = p(pid)

      // Prepare inputs
      var input = Vector[(Any, Iterable[Any])]()
      for (i <- ins) {
        val (data, meta, op) = (i.data, i.meta, i.mOP)
        val idata = if (ret isDefinedAt data) ret(data).map { case (m, d) => d }
                    else if (context isDefinedAt data) context(data).map { case (m, d) => d }
                    else { assert(false, "ERROR: pipe " + i + "could not be found either in the context or returned results thus far!"); null }
        val imeta = if (ret isDefinedAt meta) ret(meta).map { case (m, d) => m }
                    else if (context isDefinedAt meta) context(meta).map { case (m, d) => m }
                    else { assert(false, "ERROR: pipe " + i + "could not be found either in the context or returned results thus far!"); null }
        // NOTE: the following line assumes that there are two possible operators to associate data with metadata (thanks to Kazi for finding this):
        //       1. the input metadata is input set-specific: the metadata vector must be the same size as the data vector (mOP == ":")
        //       2. the input metadata is global for all input sets (mOP == "^")
        input ++= (op match { case ":" => assert(imeta.length == idata.length, 
                                                "ERROR: can't zip together metadata and data inputs of different lengths:\n" +
                                                "meta: " + imeta + "\n" +
                                                "data: " + idata + "\n")
          imeta zip idata;
                            case "^" => idata.map { d => imeta.head -> d }
                          })
      }
        
      log("INFO", "Executing Stage")
      log("INFO", "---------------")
      log.madd("INFO", stageToString(pid, ins, pipe))
      try { ret(pid) = pipe on (input.map(_._1), input.map(_._2)); }
      catch { case e : Throwable => e.printStackTrace; System.exit(1); }
    }

    ret
  }

  def stageToString(k : Int, inputs : List[PipeInput], p : Pi) : String = {
    val sep = p match { case ComposablePipe(x) => " |> ";  case x => " :> " }
    val pre = k + " = " + inputs.mkString(" + ") + sep
    val pipe = p.toListString; //WordUtils.wrap(r(k)._2.toString, 120 - pre.length).split("""\n""");
    (0 until pipe.length).map { i => if (i == 0) pre + pipe(i) else " " * pre.length + pipe(i) }.mkString(" " + "->" + "\n")
  }
  def parseToString(r : Map[Int, (List[PipeInput], Pi)]) : String = 
    if (r != null) r.keys.toList.sortWith(_ < _).map { k => stageToString(k, r(k)._1, r(k)._2) }.mkString(";\n")
    else "parse error"

  def printParse(r : Map[Int, (List[PipeInput], Pi)]) { println(parseToString(r)); }

  def runMetaPipeDescriptor(desc : String, input : Vector[(Any, Iterable[Any])]) : Map[Int, Vector[(Any, Iterable[Any])]] = {
    var con : Map[Int, Vector[(Any, Iterable[Any])]] = new HashMap[Int, Vector[(Any, Iterable[Any])]]()
    val r = parse(desc)

    con(0) = input
    log("INFO", "Pipe Descriptor:"); log("")
    log.madd("INFO", parseToString(r)); log("")

    if (r == null) return null

    con = runPipes(r, con)
    con
  }
  def runPipeDescriptor(desc : String, input : Vector[Iterable[Any]]) : Map[Int, Vector[Iterable[Any]]] =
    runMetaPipeDescriptor(desc, input.zipWithIndex.map { case (i, m) => m -> i }).map { case (i, o) => (i, o.map { case (m, d) => d }) }
}
trait PipeEntryPoint[R] extends ExternalPipeSupport with JobQEntryPoint[R] with ProcessorRegistry

trait PipeRunner extends PipeEntryPoint[Unit] { def main(args : Array[String]) { ep(args); } }

// -------------------------------------------------------------------------------------------------------------------------------------
// REPL and script runners
// -------------------------------------------------------------------------------------------------------------------------------------
/** ==Runner for compiled scripts and full programs== */
trait InternalPipeRunner[T] extends InternalPipeSupport with ArgHandler with JobQFactory {
  def run(args : Array[String]) : T

  def ep(args : Array[String]) : T = run(parseArgs(args, log))

  def main(args : Array[String]) {ep(args)}
}