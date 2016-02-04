package sekvensa.logging

import akka.actor._
import lisa.endpoint.message.MessageLogic._
import lisa.endpoint.message._
import org.json4s._

import scala.util.Try
import com.github.nscala_time.time.Imports._



case class PatientStatus(
              patientId: Int,
              careContactId: Int,
              careContactRegistrationTime: DateTime,
              visitId: Int,
              visitRegistrationTime: DateTime,
              events: PatientEvents,
              locations: PatientLocation,
              reasonForVisits: PatientReasonForVisit,
              teams: PatientTeam,
              priorities: PatientPriority,
              doctors: PatientDoctor,
              durations: PatientDurations
)

case class PatientEvents(eventsCurrent: List[JValue],
                         eventHistory: List[JValue],
                         lastEvent: Option[JValue])

case class PatientLocation(locationCurrent: JValue,
                           locationHistory: List[JValue])

case class PatientReasonForVisit(reasonForVisitCurrent: JValue,
                                 reasonForVisitHistory: List[JValue])

case class PatientTeam(teamCurrent: JValue,
                       teamHistory: List[JValue])

case class PatientPriority(priorityCurrent: JValue,
                       priorityHistory: List[JValue])

case class PatientDoctor(doctorCurrent: JValue,
                       doctorHistory: List[JValue])

case class PatientDurations(totalTime: Int,
                            waitingSinceLast: Int,
                            waitingForDoctor: Int,
                            timeToDoctor: Option[Int])





class PatientRTFold extends Actor {

  var pats: Map[Int, List[LISAMessage]] = Map()




  def receive = {

    case mess: LISAMessage => {
      val id = mess.findAs[Int]("CareContactId").head
      val p = pats.get(id)
      pats += id -> (mess +: p.getOrElse(List()))


    }

    case "hej" => {
      println("recovered")


      lisa.endpoint.esb.LISAEndPoint.initial(context.system)
      val evahPat = context.actorOf(ProduceToEvah.props(List("patients")))
      val evahEvent = context.actorOf(ProduceToEvah.props(List("events")))

      pats.foreach{ x =>
          val p = foldPatient(x._1, x._2)
          p.foreach(evahPat ! _)

          p.foreach{ pat =>
            val ev = pat.getAs[List[JObject]]("events") getOrElse(List())
            pat.getAs[ElvisPatient]("initial").foreach{n =>
              evahEvent ! LISAMessage("event"->"new", "timestamp"->n.CareContactRegistrationTime)
            }
            ev.foreach(evahEvent ! LISAMessage(_))
          }
      }
      println("done")

    }

  }

  def removeConstants(mess: LISAMessage) = {
    val t = mess.body removeField {
      case (key, _) => key == "CareContactId" || key == "PatientId"
    }
    LISAMessage(t.asInstanceOf[JObject])
  }


  def extractTimeStamp(mess: LISAMessage) = {
    //val t = mess.findAs[DateTime]("timestamp") ++ mess.findAs[String]("timeStamp").map(new DateTime(_))
    val t2 = Try{(mess.findAs[String]("timestamp") ++ mess.findAs[String]("timeStamp")).map(
      new DateTime(_)
    )}
    if (t2.isSuccess && t2.get.nonEmpty) Some(t2.get.head) else {
      println(s"Message fail time: $t2 $mess")
      None
    }
  }

  def sort(xs: List[LISAMessage]) = {
    xs.sortWith { (a, b) =>
      val res = for {
        t1 <- extractTimeStamp(a)
        t2 <- extractTimeStamp(b)
      } yield t1 < t2
      if (res.isDefined) res.get else false
    }
  }

  def extractfromRemove(xs: List[LISAMessage]) = {
    val removes = xs.flatMap { mess =>
      mess.getAs[RemovedPatient]("removed").map{ r =>
        val ev = r.patient.Events.map{e =>
          LISAMessage(
            "event"-> (e.Title),
            "staff"-> {if (e.Title == "Läkare") Some(e.Value) else None},
            "duration"-> {if (e.Start <= e.End) (e.Start to e.End).millis/60000 else 0},
            "timestamp"->e.Start,
            "patientID" -> r.patient.PatientId,
            "careContactID" -> r.patient.CareContactId
          )
        }
        val removeEvent = LISAMessage(
          "event"-> "removed",
          "duration"-> 0,
          "timestamp"->r.timestamp,
          "patientID" -> r.patient.PatientId,
          "careContactID" -> r.patient.CareContactId)
        (r.timestamp, ev :+ removeEvent)
      }
    }.sortWith(_._1 > _._1)
    if (removes.nonEmpty) removes.head._2 else List()
  }

  def extractDiffs(xs: List[LISAMessage]) = {
    xs.flatMap{ mess =>
      mess.getAs[PatientDiff]("diff").flatMap{ diff =>
        val t = extractTimeStamp(mess)
        val filter = diff.updates - "CareContactId" - "PatientId" - "timeStamp"
        if (filter.isEmpty) None
        else Some(
          LISAMessage(
            "event"->"update",
            "attributes" -> filter,
            "duration" -> 0,
            "timestamp" -> t
          ))
      }
    }
  }

  def extractNew(xs: List[LISAMessage]) = {
    val news = xs.flatMap { mess =>
      mess.getAs[NewPatient]("new").map{ n =>
        val t = n.timestamp
        val x = n.patient.copy(Events = List())
        (t, LISAMessage("initial"->x))
      }
    }.sortWith(_._1 < _._1)
    Try(news.head._2)
  }


  def dropEqual(xs: List[(Option[DateTime], String)]): List[(Option[DateTime], String)] = {
    xs match {
      case Nil => List()
      case x :: Nil => List(x)
      case x :: y :: xs if x._2 == y._2 => dropEqual(y :: xs)
      case x :: xs => x :: dropEqual(xs)
    }
  }

  def extractLocationSequence(xs: List[LISAMessage]) = {
    val locations = sort(xs).flatMap{ x =>
      val t = extractTimeStamp(x)
      val loc = x.findAs[String]("Location")
      if (loc.nonEmpty) Some((t, loc.head)) else None
    }
    val locWithTime = dropEqual(locations).map(kv => LISAMessage.body("timestamp"->kv._1, "location"->kv._2))
    LISAMessage("locations"->locWithTime)
  }

  // to be moved to LISAMessageLogic
  def findAnyObjectsWithFields(mess: LISAMessage, fields: List[JField]) = {
    mess.body.filter {
      case JObject(xs) => {fields.forall(xs contains)}
      case _ => false
    }.map(_.asInstanceOf[JObject])
  }

  def extractDurations(pat: LISAMessage) = {
    val times = pat.findAs[DateTime]("timestamp").filter(d => d > new DateTime("2015-01-05") && d < new DateTime("2016-01-05")).sortWith(_ < _)
    val toDoctor = findAnyObjectsWithFields(pat, List(("event",JString("Läkare")))).map(o => new DateTime((o \ "timestamp").asInstanceOf[JString].s))
    val tT = (times.head to times.last).millis/60000
    val tTD = {if (toDoctor.nonEmpty)(times.head to toDoctor.head).millis/60000 else 0}

    if (times.size >=2) {
      Some(LISAMessage(
        "start" -> times.head,
        "complete" -> times.last,
        "doctor" -> {if (toDoctor.nonEmpty) Some(toDoctor.head) else None},
        "totalTime" -> tT,
        "timeToDoctor" -> tTD
      ))
    } else None
  }

  def foldPatient(id: Int, patMess: List[LISAMessage]) = {
    val e = {extractfromRemove(patMess)}
    val n = {extractNew(patMess)}
    val d = {extractDiffs(patMess)}
    val locs = {extractLocationSequence(patMess)}
    if (e.nonEmpty && n.isSuccess) {
      val events = sort(e ++ d)
      Some(n.get + ("events" -> events.map(_.body)) + locs.body).map{p =>
        val dur = extractDurations(p).map(_.body).getOrElse(JObject())
        p + dur + ("lisaID" -> id)
      }
    } else None
  }



}

object PatientRTFold {
  def props(pT: List[String]) =
    Props[PatientRTFold]
}

