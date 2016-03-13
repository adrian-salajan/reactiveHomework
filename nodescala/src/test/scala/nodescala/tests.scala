package nodescala

import scala.language.postfixOps
import scala.util.{Try, Success, Failure}
import scala.collection._
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.async.Async.{async, await}
import org.scalatest._
import NodeScala._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class NodeScalaSuite extends FunSuite {

  test("A Future should always be completed") {
    val always = Future.always(517)

    assert(Await.result(always, 0 nanos) == 517)
  }
  test("A Future should never be completed") {
    val never = Future.never[Int]

    try {
      Await.result(never, 1 second)
      assert(false)
    } catch {
      case t: TimeoutException => // ok!
    }
  }

  test("all(List[Future]) gives a Future[List]") {
    var listOfFutures = List[Future[Int]](Future.successful(2))

    val futureList = Future.all(listOfFutures)

    futureList.onSuccess {
      case x => assert(x.size == 1 && x(0) == 2)
    }
  }

  test("all(List[Future]) gives a Future[List] in same order") {
    var listOfFutures = List[Future[Int]](Future.successful(2), Future.successful(3))

    val futureList = Future.all(listOfFutures)

    futureList.onSuccess {
      case x => assert(x.size == 2 && x(0) == 2 && x(1) == 3)
    }
  }

  test ("all: if any future fails than the result fails") {
    val listOfFutures = List[Future[Int]](Future.successful(2), Future.failed(new Exception("failed 2nd future")))

    val futureList = Future.all(listOfFutures)

    futureList.onSuccess {
      case x => fail("should not be success when one element fails")
    }

    futureList.onFailure {
      case f => f.getMessage == "failed 2nd future"
    }
  }


  test("any: return result from list w/ one element success") {
    val list = List(Future(2))

    Future.any(list).onComplete( ti => assert(ti.get == 2))
  }

  test("any: return result from list w/ one element failure") {
    val list = List(Future.failed(new Exception("failed")))

    Future.any(list).onComplete( ti => assert(ti.isFailure))
  }

  test("any: return first result from list") {
    val list = List(Future(2), Future.never)

    Future.any(list).onComplete( ti => assert(ti.get == 2))
  }

  test("delay") {
    val delayed = Future.delay(1.seconds)
    assert(!delayed.isCompleted)
    Thread.sleep(1.seconds + 10.millis toMillis)
    assert(delayed.isCompleted)
  }

  test("any & delay: return failure for emptylist") {
    val list = List[Future[Unit]]()

    val first = Future.any(list)
    assert(first.isCompleted)
    assert(first.value.get.isFailure)
  }

  test("any & delay: return first completed result from list") {
    val list = List(Future.never, Future.delay(1.seconds), Future.delay(100.seconds))

    val first = Future.any(list)
    assert(!first.isCompleted)
    Thread.sleep(1010)
    assert(first.isCompleted)
  }

  test("canceled after unsubscribe") {
    val promise = Promise()
    val cancelable = Future.run()(ct => Future {
      while(!ct.isCancelled) {
        Thread.sleep(100)
      }
      promise.failure(new Exception("canceled"))
    })

    assert(!promise.isCompleted)

    Thread.sleep(1.seconds toMillis)
    assert(!promise.isCompleted)

    cancelable.unsubscribe()
    Thread.sleep(100)
    assert(promise.isCompleted)
  }

  test("now uncompleted future throws exception") {
    try {
      Future.never.now
      assert(false)
    } catch {
      case e: Exception => {}
    }
  }

  test("now completed future returns result") {
    val v = Future.successful(2).now
    assert(v == 2)
  }

  test("now completed future returns failed result") {
    try {
      Future.failed(new Exception("future result is failed"))
      assert(false)
    } catch {
      case e: Exception => {}
    }
  }

  test("continueWith with result of that succesfull") {
   val f = Future {
        2
    }.continueWith(fu => 4)
  Thread.sleep(100)
    assert(f.value.get.get == 4)

  }

  test("continueWith with result of that failed") {
    val f = Future.failed[Int](new Exception()).continueWith(fu => 4)
    Thread.sleep(100)
    assert(f.value.get.isFailure)

  }

  test("continue with result of that succesfull") {
    val f = Future {
      2
    }.continue(v => v.get + 2)
    Thread.sleep(100)
    assert(f.value.get.get == 4)

  }

  test("continue with result of that failure") {
    val f = Future.failed[Int](new Exception).continue(v => 4)
    Thread.sleep(100)
    assert(f.value.get.isFailure)

  }

  
  
  class DummyExchange(val request: Request) extends Exchange {
    @volatile var response = ""
    val loaded = Promise[String]()
    def write(s: String) {
      response += s
    }
    def close() {
      loaded.success(response)
    }
  }

  class DummyListener(val port: Int, val relativePath: String) extends NodeScala.Listener {
    self =>

    @volatile private var started = false
    var handler: Exchange => Unit = null

    def createContext(h: Exchange => Unit) = this.synchronized {
      assert(started, "is server started?")
      handler = h
    }

    def removeContext() = this.synchronized {
      assert(started, "is server started?")
      handler = null
    }

    def start() = self.synchronized {
      started = true
      new Subscription {
        def unsubscribe() = self.synchronized {
          started = false
        }
      }
    }

    def emit(req: Request) = {
      val exchange = new DummyExchange(req)
      if (handler != null) handler(exchange)
      exchange
    }
  }

  class DummyServer(val port: Int) extends NodeScala {
    self =>
    val listeners = mutable.Map[String, DummyListener]()

    def createListener(relativePath: String) = {
      val l = new DummyListener(port, relativePath)
      listeners(relativePath) = l
      l
    }

    def emit(relativePath: String, req: Request) = this.synchronized {
      val l = listeners(relativePath)
      l.emit(req)
    }
  }
  test("Server should serve requests") {
    val dummy = new DummyServer(8191)
    val dummySubscription = dummy.start("/testDir") {
      request => for (kv <- request.iterator) yield (kv + "\n").toString
    }

    // wait until server is really installed
    Thread.sleep(500)

    def test(req: Request) {
      val webpage = dummy.emit("/testDir", req)
      val content = Await.result(webpage.loaded.future, 1 second)
      val expected = (for (kv <- req.iterator) yield (kv + "\n").toString).mkString
      assert(content == expected, s"'$content' vs. '$expected'")
    }

    test(immutable.Map("StrangeRequest" -> List("Does it work?")))
    test(immutable.Map("StrangeRequest" -> List("It works!")))
    test(immutable.Map("WorksForThree" -> List("Always works. Trust me.")))

    dummySubscription.unsubscribe()
  }

}




