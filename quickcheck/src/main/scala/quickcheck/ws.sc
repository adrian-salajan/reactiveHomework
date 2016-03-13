def isIncreasing(li :List[Int]) : Boolean = li match {
   case List(a) => true
   case a :: b :: _ => (a <= b) && isIncreasing(li.tail)
}

println (isIncreasing(List(1, 3, 3, 4)))