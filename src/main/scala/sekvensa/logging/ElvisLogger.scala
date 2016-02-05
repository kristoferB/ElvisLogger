package sekvensa.logging

import akka.actor._
import akka.persistence._
import lisa.endpoint.message._
import lisa.endpoint.message.MessageLogic._


/**
 * Created by kristofer on 18/02/15.
 */
class ElvisLogger extends PersistentActor {
  override def persistenceId = "ELVIS"

  //override def preStart() = ()

  var currentState: List[ElvisPatient] = List()

  import org.json4s._
  import org.json4s.native.JsonMethods._
  import org.json4s.native.Serialization
  import org.json4s.native.Serialization.{read, write}
  import com.github.nscala_time.time.Imports._
  implicit val formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all

  //lisa.endpoint.esb.LISAEndPoint.initial(context.system)
  val evah = context.actorOf(dummyProduceToEvah.props(List("events")))

  def receiveCommand = {
    case "hej" => evah ! "hej"
    case s @ SnapShot(ps) => {
      if (currentState.isEmpty) {
        ps.foreach(p => println(s"${p.CareContactId}, ${p.Location}"))
        persist(s)(event => println("persisted a snapshot"))
        currentState = ps
      }
      else if (currentState == ps) "hej" //println("NO CHANGE")
      else {
        val changes = ps.filterNot(currentState.contains)
        val removed = currentState.filterNot(p => ps.exists(_.CareContactId == p.CareContactId))
        changes.map{p =>
          val old = currentState.find(_.CareContactId == p.CareContactId)
          //println(s"OLD: $old")
          //println(s"NEW: $p")

          val diffP = diffPat(p, old)
          diffP match {
            case None => {
              val newPatient = NewPatient(getNow, p)
              persist(newPatient)(e => println(s"persisted a NEW: $e"))
            }
            case Some(d) => {
              val diffPatient = PatientDiff(d._1, d._2, d._3)
              persist(diffPatient) { e =>
                println("")
                println(s"persisted a Diff")
                println(s"old pat: $old")
                println(s"new pat: $p")
                println(s"diff: $diffPatient")
                println("")
              } //println(s"persisted a diff: $diffPatient"))
            }
          }
        }
        removed.map{p =>
          val removedPat = RemovedPatient(getNow, p)
          persist(removedPat)(e => println(s"persisted a remove: $e"))

        }
        currentState = ps
      }

    }
    case mess @ _ => println(s"ElvisLogger got: $mess")
  }
  var i = 0
  var xs = List[PatientDiff]()
  val receiveRecover: Receive = {
    case d: PatientDiff => {
      sendToEvah(LISAMessage("diff"->d))
//      xs = d :: xs
//      if (i == 10){
//        val j = LISAMessage("events"->xs).bodyToJson
//        println(j)
//      }
//      i += 1
    }
    case np: NewPatient => sendToEvah(LISAMessage("new"->np))
    case s: SnapShot =>  {
      //println("Got snap")
      //val j = LISAMessage("hej"->s).bodyToJson
      //println(j)
      s.patients.foreach(p => sendToEvah(LISAMessage("new"->toNewPat(p))))
    };
    case r: RemovedPatient => sendToEvah(LISAMessage("removed"->r))
  }


  def sendToEvah(mess: LISAMessage) = {
    evah ! mess
  }

  def toNewPat(p: ElvisPatient)= {
    val t = p.CareContactRegistrationTime
    NewPatient(t,p)
  }


  def diffPat(curr: ElvisPatient, old: Option[ElvisPatient])={
    old.map {
      case prev: ElvisPatient => {
        (Map(
          "CareContactId" -> Some(Extraction.decompose(curr.CareContactId)),
          "CareContactRegistrationTime" -> diffThem(prev.CareContactRegistrationTime, curr.CareContactRegistrationTime),
          "DepartmentComment" -> diffThem(prev.DepartmentComment, curr.DepartmentComment),
          "Location" -> diffThem(prev.Location, curr.Location),
          "PatientId" -> Some(Extraction.decompose(curr.PatientId)),
          "ReasonForVisit" -> diffThem(prev.ReasonForVisit, curr.ReasonForVisit),
          "Team" -> diffThem(prev.Team, curr.Team),
          "VisitId" -> diffThem(prev.VisitId, curr.VisitId),
          "VisitRegistrationTime" -> diffThem(prev.VisitRegistrationTime, curr.VisitRegistrationTime),
          "timestamp" -> Some(Extraction.decompose(getNow))
        ).filter(kv=> kv._2 != None).map(kv=> kv._1 -> kv._2.get),
          curr.Events.filterNot(prev.Events.contains),
          prev.Events.filterNot(curr.Events.contains))
      }
    }

  }

  def diffThem[T](prev: T, current: T): Option[JValue]= {
    if (prev == current) None
    else Some(Extraction.decompose(current))
  }

  def getNow = {
    DateTime.now(DateTimeZone.forID("Europe/Stockholm"))
  }
//
//
//  def diff(orig: Product, update: Product): Map[Int, Any] = {
//    assert(orig != null && update != null, "Both products must be non-null")
//    assert(orig.getClass == update.getClass, "Both products must be of the same class")
//
//    val diffs = for (ix <- 0 until orig.productArity) yield {
//      (orig.productElement(ix), update.productElement(ix)) match {
//        case (s1: String, s2: String) if (!s1.equalsIgnoreCase(s2)) => Some((ix -> s2))
//        case (s1: String, s2: String) => None
//        case (p1: Product, p2: Product) if (p1 != p2) => Some((ix -> diff(p1, p2)))
//        case (x, y) if (x != y) => Some((ix -> y))
//        case _ => None
//      }
//    }
//
//    diffs.flatten.toMap
//  }

}

import lisa.endpoint.esb._

class ProduceToEvah(prop : LISAEndPointProperties) extends LISAEndPoint(prop) {
  def receive = {
    case mess: LISAMessage => {
      topics ! mess
    }
  }
}

object ProduceToEvah {
  def props(pT: List[String]) =
    Props(classOf[ProduceToEvah], LISAEndPointProperties("produceToEvah", List(), pT))
}










import shapeless._
class TESTAR extends App {
  case class Foo(i : Int, s : String, list: List[String])

  val f1 = Foo(1, "hej", List("hej"))
  val f2 = Foo(2, "hej", List("hej"))






  def diff(orig: Product, update: Product): Map[Int, Any] = {
    assert(orig != null && update != null, "Both products must be non-null")
    assert(orig.getClass == update.getClass, "Both products must be of the same class")

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

  println("DIFF: "+diff(f1,f2))

}

object ElvisLogger {
  def props = Props[ElvisLogger]


}

