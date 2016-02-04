package sekvensa.logging

import akka.actor.Status.Success
import akka.actor._
import lisa.endpoint.esb._
import lisa.endpoint.message._
import org.joda.time.Minutes
import org.json4s._
import lisa.endpoint.message.MessageLogic._

import scala.concurrent.{Await, Future}
import scala.util.Try

/**
 * Created by kristofer on 15-06-10.
 */
class dummyProduceToEvah extends Actor {

  import context.dispatcher

  var pats: Map[Int, List[LISAMessage]] = Map()

  def receive = {
    case "hej" => {
      println("recovered")

      import com.github.nscala_time.time.Imports._

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
        val removeMessage = (xs.flatMap(
          _.getAs[RemovedPatient]("removed").map(r=>
            (r.timestamp, r)))).sortWith(_._1 > _._1).map(_._2).headOption

        removeMessage.map{r =>
          val ev = r.patient.Events.map{e =>
            (e.Start, LISAMessage(
              "event"-> (e.Title),
              "staff"-> {if (e.Title == "Läkare") Some(e.Value) else None},
              "duration"-> {if (e.Start <= e.End) (e.Start to e.End).millis/60000 else 0},
              "timestamp"->e.Start,
              "patientID" -> r.patient.PatientId,
              "careContactID" -> r.patient.CareContactId
              //"lisaID"->{"evID"+e.CareEventId.toString+e.Start.toString}
            ))
          }.sortWith(_._1 < _._1)

          val remTime = if (ev.nonEmpty && r.timestamp < ev.last._1)
            r.timestamp.plusHours(1) else r.timestamp

          val removeEvent = LISAMessage(
            "event"-> "removed",
            "duration"-> 0,
            "timestamp"->remTime,
            "patientID" -> r.patient.PatientId,
            "careContactID" -> r.patient.CareContactId
            //"lisaID"-> {r.patient.CareContactId + remTime.toString}
          )

          ev.map(_._2) :+ removeEvent
        }.getOrElse(List[LISAMessage]())

      }

      def extractDiffs(xs: List[LISAMessage]) = {
        xs.flatMap{ mess =>
          mess.getAs[PatientDiff]("diff").flatMap{ diff =>
            val t = extractTimeStamp(mess)
            val filter = diff.updates - "CareContactId" - "PatientId" - "timeStamp" - "timestamp"
            val id = "evID"+diff.updates("CareContactId").toString + t.toString
            if ((filter ).isEmpty) None
            else Some(
              LISAMessage(
                "event"->"update",
                "attributes" -> filter,
                "duration" -> 0,
                "timestamp" -> t.map(_.plusHours(1)),
                "careContactID"-> diff.updates("CareContactId"),
                "patientID" -> diff.updates("PatientId")
                //"lisaID" -> id
              ))
          }
        }
      }

      def extractNew(xs: List[LISAMessage]) = {
        val newMessage = (xs.flatMap(
          _.getAs[NewPatient]("new").map(n=>
            (n.timestamp, n)))).sortWith(_._1 > _._1).map(_._2).headOption

        newMessage.map{n =>
          val p = n.patient.copy(Events = List())
          val t = n.patient.CareContactRegistrationTime
          LISAMessage(
            "initial" -> p,
            "timestamp" -> t
          )
        }
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
        if (e.nonEmpty && n.nonEmpty) {
          val events = sort(e ++ d)
          Some(n.get + ("events" -> events.map(_.body)) + locs.body).map{p =>
            val dur = extractDurations(p).map(_.body).getOrElse(JObject())
            p + dur + ("lisaID" -> id)
          }
        } else None
      }

      lisa.endpoint.esb.LISAEndPoint.initial(context.system)
      val evahPat = context.actorOf(ProduceToEvah.props(List("akutenpatients")))
      val evahEvent = context.actorOf(ProduceToEvah.props(List("akutenevents")))
      //val evahRaw = context.actorOf(ProduceToEvah.props(List("raw")))

      pats.foreach{ x =>
          //x._2.foreach(evahRaw ! _)

          val p = foldPatient(x._1, x._2)
          p.foreach(evahPat ! _)

          p.foreach{ pat =>
            val ev = pat.getAs[List[JObject]]("events") getOrElse(List())
            pat.getAs[ElvisPatient]("initial").foreach{n =>
              evahEvent ! LISAMessage(
                "event"->"register",
                //"lisaID" -> {"evID"+n.CareContactId.toString+pat.get("timestamp").toString},
                "initial"->n,
                "timestamp" -> pat.get("timestamp")
              )
            }
            ev.foreach(evahEvent ! LISAMessage(_))
          }
      }
      println("done")



    }
    case mess: LISAMessage => {
      val id = mess.findAs[Int]("CareContactId").head
      val p = pats.get(id)
      pats += id -> (mess +: p.getOrElse(List()))


//      val d = mess.getAs[PatientDiff]("diff")
//      val np = mess.getAs[NewPatient]("new")
//      val s = mess.getAs[SnapShot]("state")
//      val r = mess.getAs[RemovedPatient]("removed")
//
//      np.foreach { patient =>
//        val id = patient.patient.CareContactId
//        xs += (id->(mess +: xs.get(id).getOrElse(List())))
//
//        //if (re.contains(id)) println("pat was removed but added: "+id)
//      }
//
//      d.foreach { diff =>
//        val id = mess.findAs[Int]("CareContactId").head
//        val p = xs.get(id)
//        xs += id -> (mess +: p.getOrElse(List()))
//
//        //if (re.contains(id)) println("pat was removed but added: "+id)
//      }
//
//      r.foreach { removed =>
//        val id = mess.findAs[Int]("CareContactId").head

        //xs = xs - id

        //re = re + id
        //println(s"removed pat: $id, pats left: ${xs.size}")
//        if (id.contains(ccID)){
//          import com.github.nscala_time.time.Imports._
//          xs = removeConstants(mess) :: xs
//          val tEnd = mess.findAs[DateTime]("timestamp")
//          val first = xs.last.findAs[DateTime]("CareContactRegistrationTime")
//
//          val diff = first.head to tEnd.head
//          println(s"A patient time: ${diff.toDuration}")
//          //xs.foreach(println)
//          ccID = -1
//        }

//      }
    }
  }

  def removeConstants(mess: LISAMessage) = {
    val t = mess.body removeField {
      case (key, _) => key == "CareContactId" || key == "PatientId"
    }
    LISAMessage(t.asInstanceOf[JObject])
  }


}

object dummyProduceToEvah {
  def props(pT: List[String]) =
    Props[dummyProduceToEvah]
}

