/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import akka.actor._
import scala.collection.immutable.Queue

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef
    def id: Int
    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection*/
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply
  
  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

}


class BinaryTreeSet extends Actor {
  import BinaryTreeSet._
  import BinaryTreeNode._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

  var root = createRoot

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  def receive = normal

  // optional
  /** Accepts `Operation` and `GC` messages. */
  val normal: Receive = {
    case insert:Insert => {
      root.forward(insert)
    }
    case contains: Contains => {
      root.forward(contains)
    }
    case remove: Remove => {
      root.forward(remove)
    }

    case GC => {
//      val oldRoot = root
//      root = createRoot
      val newRoot = createRoot
      root ! CopyTo(newRoot)
      root = newRoot
      context.become(garbageCollecting(newRoot))
    }
    case a:Any => {
      println("!!!!!!!!!!!!!!!!!!!!!!!!>>>" + a)
      context.stop(sender)
    }
  }

  // optional
  /** Handles messages while garbage collection is performed.
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  def garbageCollecting(newRoot: ActorRef): Receive = {
    case GC => { println("ignored <GC:GC>")}
    case CopyFinished => {
      println("FINISHED <GC:GC>")
      context.become(normal)
      while(!pendingQueue.isEmpty) {
        val msg = pendingQueue.head
        newRoot ! msg
        pendingQueue = pendingQueue.tail
      }
    }
    case op: Operation => {
      pendingQueue = pendingQueue enqueue op
      //newRoot forward msg
      println("enquued msg = " + op)
    }
  }

}

object BinaryTreeNode {
  trait Position

  case object Left extends Position
  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)
  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode],  elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor {
  import BinaryTreeNode._
  import BinaryTreeSet._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved



  // optional


  // optional
  def receive = normal

  /** Handles `Operation` messages and `CopyTo` requests. */
  val normal: Receive = {
    case Insert(req, id, newElem) => insert(req, id, newElem)
    case Contains(req, id, query) => contains(req, id, query)
    case Remove(req, id, elem) => remove(req, id, elem)
    case CopyTo(newTree) => copyTo(newTree)
    case a:Any => println("!!!!!!!!!!!!!inNode!>>>" + a) }

  def copyTo(newTree: ActorRef): Unit = {
    if (subtrees.isEmpty && removed) context.parent ! CopyFinished
    else {
      val actorSet = subtrees.values.toSet[ActorRef]
      context.become(copying(actorSet, removed))
      if (!removed) {
        newTree ! Insert(context.self, elem, elem)
      }
      actorSet.foreach(_ ! CopyTo(newTree))
    }
  }

  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  def copying(expected: Set[ActorRef], insertConfirmed: Boolean): Receive = {
    case op: OperationFinished => {
      if (expected.isEmpty /*&& insertConfirmed*/) {
        context.parent ! CopyFinished
        println(this.elem +" sending CopyFInished")
      } else {
        context.become(copying(expected, true))
      }
    }

    case CopyFinished => {
      val remainingActors = expected - sender
      context.stop(sender)
      println("got CopyFInished")
      if (remainingActors.isEmpty && insertConfirmed) {
        println(this.elem +" sending CopyFInished55555555")
        context.parent ! CopyFinished
      }
      else context.become(copying(remainingActors, insertConfirmed))
    }
  }

  def getPosition(newElem: Int): Position = {
    if (newElem > elem) Right
    else Left
  }

  def insert(requester: ActorRef, id: Int, newElem: Int): Unit = {
    if (newElem == elem) sender ! OperationFinished(id)
    else {
      val pos = getPosition(newElem)
      subtrees.get(pos).fold{
        subtrees = subtrees + (pos -> context.actorOf(props(newElem, false)))
        requester ! OperationFinished(id)
      }{
        _.forward(Insert(requester, id, newElem))
      }
    }
  }



  def contains(requester: ActorRef, id: Int, query: Int): Unit = {
    if (elem == query && !removed ) requester ! ContainsResult(id, true) else
      if (subtrees.isEmpty) requester ! ContainsResult(id, false)
      else
      subtrees.get(getPosition(query)).fold
      {
        requester ! ContainsResult(id, false)
      }{
        _.forward(Contains(requester, id, query))
      }
  }

  def remove(requester: ActorRef, id: Int, e: Int): Unit = {
    if (elem == e) { removed = true; requester ! OperationFinished(id)}  else
      if (subtrees.isEmpty) requester ! OperationFinished(id)
      else subtrees.get(getPosition(e)) match {
        case Some(a) => a.forward(Remove(requester, id, e))
        case None => requester ! OperationFinished(id)
      }
  }




}
