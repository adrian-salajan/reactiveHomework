package suggestions


import org.scalatest.time.Seconds

import language.postfixOps
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try, Success, Failure}
import rx.lang.scala._
import org.scalatest._
import gui._

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner


@RunWith(classOf[JUnitRunner])
class WikipediaApiTest extends FunSuite {

  object mockApi extends WikipediaApi {
    def wikipediaSuggestion(term: String) = Future {
      if (term.head.isLetter) {
        for (suffix <- List(" (Computer Scientist)", " (Footballer)")) yield term + suffix
      } else {
        List(term)
      }
    }
    def wikipediaPage(term: String) = Future {
      "Title: " + term
    }
  }

  import mockApi._

  test("WikipediaApi should make the stream valid using sanitized") {
    val notvalid = Observable.just("erik", "erik meijer", "martin")
    val valid = notvalid.sanitized

    var count = 0
    var completed = false

    val sub = valid.subscribe(
      term => {
        assert(term.forall(_ != ' '))
        count += 1
      },
      t => assert(false, s"stream error $t"),
      () => completed = true
    )
    assert(completed && count == 3, "completed: " + completed + ", event count: " + count)
  }

  test("recovered stream with success") {
    val obs = Observable.just("a", "b")

    obs.recovered.subscribe(e => {

      assert(e.isSuccess, e)
    })
  }

  test("recovered stream with failure") {
    val obs: Observable[String] = Observable.create( obs => {
      //obs.onNext("a")
      obs.onError(new Exception)
      Subscription.apply()
    })

    obs.recovered.subscribe(t => {
      assert(t.isFailure, t)
    })
  }

  test("recovered stream with success then failure") {

    val ex = new Exception
    val obs: Observable[String] = Observable.create( obs => {
      obs.onNext("a")
      obs.onError(ex)
      obs.onNext("b")
      Subscription.apply()
    })

    val li = obs.recovered.toBlocking.toList
    assert(li == Success("a") :: Failure(ex) :: Nil)

  }

//  test("timeout") {
//    val timeline = Observable.interval(2 seconds)
//
//    val ns = timeline.timedOut(7).toBlocking.toList
//
//    assert(0 :: 1 :: 2 :: Nil == ns, ns)
//  }
//
//  test("timeout, complete before") {
//    val timeline = Observable.interval(1 seconds).take(4.5 seconds)
//
//    val ns = timeline.timedOut(7).toBlocking.toList
//
//    assert(0 :: 1 :: 2 :: 3 :: Nil == ns, ns)
//  }

  test ("concat recover") {
    val obs = Observable.just(1, 2 ,3 ,4)
    val m = (n:Int) => if (n == 3) Observable.error(new Exception) else Observable.just(n)

    val recov = obs.concatRecovered(m)
    val li = recov.toBlocking.toList
    assert(li.size == 4, li)
  }

  test("WikipediaApi should correctly use concatRecovered") {
    val requests = Observable.just(1, 2, 3)
    val remoteComputation = (n: Int) => Observable.just(0 to n : _*)
    val responses = requests concatRecovered remoteComputation
    val sum = responses.foldLeft(0) { (acc, tn) =>
      tn match {
        case Success(n) => acc + n
        case Failure(t) => throw t
      }
    }
    var total = -1
    val sub = sum.subscribe {
      s => total = s
    }
    assert(total == (1 + 1 + 2 + 1 + 2 + 3), s"Sum: $total")
  }

}
