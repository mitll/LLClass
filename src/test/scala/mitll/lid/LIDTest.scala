/*
 *  Written by Wade Shen <swade@ll.mit.edu>
 *  Copyright 2005-2016 Massachusetts Institute of Technology, Lincoln Laboratory
 *  Revision: 0.2
 */

package mitll.lid

import collection.mutable.Stack
import org.scalatest._

class LIDSpec extends FlatSpec with Matchers {

/*  "A Stack" should "pop values in last-in-first-out order" in {
    val stack = new Stack[Int]
    stack.push(1)
    stack.push(2)
    stack.pop() should be (2)
    stack.pop() should be (1)
  }

  it should "throw NoSuchElementException if an empty stack is popped" in {
    val emptyStack = new Stack[Int]
    a [NoSuchElementException] should be thrownBy {
      emptyStack.pop()
    }
  }*/

  it should "train a model" in {
    val args = "-all test/news4L-500each.tsv.gz -split 0.15 -iterations 10"
 //   val res = new LID().main(args.split(" "))
    val res: Unit = new LID().ep(args.split(" "))
    System.out.println(s"res $res")
  }
}
