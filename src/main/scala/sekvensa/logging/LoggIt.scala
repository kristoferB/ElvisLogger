package sekvensa.logging

import akka.actor.ActorSystem
import org.json4s.JValue
import scala.concurrent._



//import sekvensa.elvis._
import com.github.nscala_time.time.Imports._
import scala.concurrent.duration._


case class ElvisPatient(CareContactId: Int,
                        CareContactRegistrationTime: DateTime,
                        DepartmentComment: String,
                        Events: List[ElvisEvent],
                        Location: String,
                        PatientId: Int,
                        ReasonForVisit: String,
                        Team: String,
                        VisitId: Int,
                        VisitRegistrationTime: DateTime)


case class ElvisEvent(CareEventId: Int,
                      Category: String,
                      End: DateTime,
                      Start: DateTime,
                      Title: String,
                      Type: String,
                      Value: String,
                      VisitId: Int)


case class PatientDiff(updates: Map[String, JValue], newEvents: List[ElvisEvent], removedEvents: List[ElvisEvent])
case class NewPatient(timestamp: DateTime, patient: ElvisPatient)
case class RemovedPatient(timestamp: DateTime, patient: ElvisPatient)
case class SnapShot(patients: List[ElvisPatient])

object LoggIt extends App {
  val system = ActorSystem("ELVISLogger")
  val logger = system.actorOf(ElvisLogger.props, "logger")
  logger ! "hej"
  //val comm = system.actorOf(ElvisComm.props(logger), "comm")

  //comm ! "GET"

  Console.readLine() // wait for enter to exit
  system.terminate()

//  case class Foo(i : Int, s : String, list: List[String])
//
//  val f1 = Foo(1, "hej", List("hej"))
//  val f2 = Foo(2, "kalle", List( "då"))
//
//
//  //println(f1.delta(f2))
//    import org.json4s._
//    import org.json4s.native.JsonMethods._
//    import org.json4s.native.Serialization
//    import org.json4s.native.Serialization.{read, write}
//    implicit val formats = Serialization.formats(NoTypeHints)
//
//    val ser1 = parse(write(f1))
//    val ser2 = parse(write(f2))
//
//    val diff = ser1 diff ser2
//
//    println(diff)




  def diff(orig: Product, update: Product): Map[Int, Any] = {
    assert(orig != null && update != null, "Both products must be non-null")
    //assert(orig.getClass == update.getClass, "Both products must be of the same class")

    val diffs = for (ix <- 0 until orig.productArity) yield {
      (orig.productElement(ix), update.productElement(ix)) match {
        case (s1: String, s2: String) if (!s1.equalsIgnoreCase(s2)) => Some((ix -> s2))
        case (s1: String, s2: String) => None
        case (p1: Product, p2: Product) if (p1 != p2) => Some((ix -> diff(p1, p2)))
        case (x, y) if (x != y) => Some((ix -> y))
        case _ => None
      }
    }

    diffs.flatten.toMap
  }

  //println("DIFF: "+diff(f1,f2))





  //val system = ActorSystem("ELVISLogger")




















//  val system = ActorSystem("Logger")

//  val pingActor = system.actorOf(PingActor.props, "pingActor")
//  pingActor ! PingActor.Initialize
//  // This example app will ping pong 3 times and thereafter terminate the ActorSystem -
//  // see counter logic in PingActor
//  system.awaitTermination()
}

