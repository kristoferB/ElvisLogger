package sekvensa.logging

import akka.actor._
import akka.persistence._

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

  def receiveCommand = {
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
              persist(newPatient)(e => println(s"persisted a NEW: $newPatient"))
            }
            case Some(d) => {
              val diffPatient = PatientDiff(d._1, d._2, d._3)
              persist(diffPatient)(e => "hej") //println(s"persisted a diff: $diffPatient"))
            }
          }
        }
        removed.map{p =>
          val removedPat = RemovedPatient(getNow, p)
          persist(removedPat)(e => println(s"persisted a remove: $removedPat"))

        }
        currentState = ps
      }

    }
    case mess @ _ => println(s"ElvisLogger got: $mess")
  }

  val receiveRecover: Receive = {
    case d: PatientDiff => println(s"RECOVER DIFF")
    case np: NewPatient => println(s"RECOVER NEW")
    case s: SnapShot => println(s"RECOVER SNAP")
    case r: RemovedPatient => println(s"RECOVER removed")


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
          "timeStamp" -> Some(Extraction.decompose(getNow))
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

