package quickcheck

import common._

import org.scalacheck._
import Arbitrary._
import Gen._
import Prop._

import scala.collection.immutable.Nil

abstract class QuickCheckHeap extends Properties("Heap") with IntHeap {

  property("min1") = forAll { a: Int =>
    val h = insert(a, empty)
    findMin(h) == a
  }

  property("heap with 1 elem, after delete is empty") = forAll { a: Int =>
    val h = insert(a, empty)
    isEmpty(deleteMin(h))
  }

  property("heap with n elem, added minimum is new minimum") = forAll { (h : H) =>
    val m = Int.MinValue
    val h2 = insert(m , h)
    findMin(h2) == m
  }

  property("heap with n elem, deleted one is minimum") = forAll { (h : H) =>
    val m = if (isEmpty(h)) 0 else findMin(h)
    val hm = deleteMin(h)
    findMin(insert(m, hm)) == m
  }

  property("empty + nonempty have same minimum") = forAll { (h : H) =>
    val m = if (isEmpty(h)) 0 else findMin(h)
    findMin(meld(empty, h)) == m
  }

  property("nonempty + nonempty have min") = forAll { (h :H, h2 : H) =>
    val m1 = if (isEmpty(h)) 0 else findMin(h)
    val m2 = if (isEmpty(h2)) 0 else findMin(h2)
    val m = if (m1 < m2) m1 else m2
    val melded = meld(h, h2)
    findMin(melded) == m
  }

//  def getAsList(h: H) : List[Int] = {
//    if (isEmpty(h)) List() else findMin(h) :: getAsList(deleteMin(h))
//  }

  def getAsList(rList : List[Int], h :H) : List[Int] = {
    if (isEmpty(h)) rList.reverse else getAsList(findMin(h) :: rList, deleteMin(h))
}

  def getAsList(h: H) : List[Int] = {
     if (isEmpty(h)) List() else getAsList(List(), h)
  }

  property ("get as list") = forAll { (a : Int) =>
    val a = insert(-2, empty)
    val b = insert(1, a)
    val c = insert(2, b)
    val li = getAsList(c)
    li.equals(List(-2, 1, 2))


  }

  def isIncreasing(li :List[Int]) : Boolean = li match {
      case List(a) => true
      case a :: b :: _ => (a <= b) && isIncreasing(li.tail)
  }

  property("isIncreasing") = forAll { (a: Int) =>
     isIncreasing(List(1,2,2,3, Math.abs(a + 5)))
  }
  property("isDecreasing") = forAll { (a: Int) =>
    isIncreasing(List(1,2,2,3, Math.abs(a + 5)).reverse) == false
  }


  property("minimum is always increasing") = forAll { (h :H) =>
     val heapList : List[Int] = getAsList(h)
     isIncreasing(heapList)
  }

  lazy val genHeap: Gen[H] = for {
    i <- arbitrary[Int]
    he <- oneOf(genHeap, const(this.empty))
  } yield insert(i, he)

  implicit lazy val arbHeap: Arbitrary[H] = Arbitrary(genHeap)

}
