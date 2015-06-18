package sekvensa.logging

import akka.actor.Status.Success
import akka.actor._
import lisa.endpoint.esb._
import lisa.endpoint.message._
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
        val removes = xs.flatMap { mess =>
          mess.getAs[RemovedPatient]("removed").map{ r =>
            val ev = r.patient.Events.map{e =>
              LISAMessage(
                  "event"-> (e.Title),
                  "staff"-> {if (e.Title == "Läkare") Some(e.Value) else None},
                  "duration"-> {if (e.Start <= e.End) (e.Start to e.End).millis/60000 else 0},
                  "timestamp"->e.Start)
            }
            val removeEvent = LISAMessage(
              "event"-> "removed",
              "duration"-> 0,
              "timestamp"->r.timestamp)
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

      //println(kalle.head)

//      println()
//
//      val k = Await.result(ps, scala.concurrent.duration.Duration(5.0, scala.concurrent.duration.SECONDS))
//      println(k)

      // extract a patient
//      val p = {
//        val (id, xs) = pats.drop(30).head
//        val removed = sort(xs.filter(_.find("removed").nonEmpty)).last
//        val events = removed.getAs[RemovedPatient]("removed").map { r =>
//            r.patient.Events.map{e =>
//              val diff = if (e.Start <= e.End)
//                (e.Start to e.End).millis/60000 else 0
//              (e.Start,
//                LISAMessage(
//                "event"-> (e.Title),
//                "staff"-> {if (e.Title == "Läkare") Some(e.Value) else None},
//                "duration"-> diff,
//                "start"->e.Start)
//                )
//            }
//          }
//
////        val diffs = sort(xs.filter(_.find("diff").nonEmpty))
////        val news = sort(xs.filter(_.find("new").nonEmpty))
//
//        val d = xs.flatMap{ mess =>
//          mess.getAs[PatientDiff]("diff").flatMap{ diff =>
//            val t = diff.updates("timeStamp") match {case JString(s)=> new DateTime(s)}
//            val filter = diff.updates - "CareContactId" - "PatientId" - "timeStamp"
//            if (filter.isEmpty) None
//            else Some((
//              t,
//              LISAMessage(
//                "event"->"update",
//                "attributes" -> filter,
//                "duration" -> 0,
//                "start" -> t
//              )))
//          }
//        }
//
//        val fromNew = xs.flatMap { mess =>
//          mess.getAs[NewPatient]("new").map{ n =>
//            val t = n.timestamp
//            val x = n.patient.copy(Events = List())
//            (t, LISAMessage("new"->x))
//          }
//        }
//
//        val allEvents = events.getOrElse(List()) ++ d
//
//        val temp = allEvents.sortWith(_._1 < _._1)
//
//        (fromNew, temp)
//      }
//
//      println(p)





//      val temp = pats.takeRight(5)
//      temp.foreach{
//        case (id, xs) => {
//          println("")
//          println("")
//          println("")
//          xs.foreach{ lm =>
//            println(lm)
//          }
//          println("")
//          println("")
//          println("")
//        }
//      }


//      val locations = pats.foldLeft(List[List[String]]())((a,b) => {
//        val locs = b._2.flatMap{
//          mess => {
//            mess.findAs[String]("Location")
//          }
//        }
//        dropEqual(locs).reverse +: a
//      })
//
//      locations.map(println)

//
//      val manyRemoved = pats.map{ p =>
//        p._1 -> p._2.map { mess =>
//          val d = mess.getAs[PatientDiff]("diff").map(x => "d")
//          val np = mess.getAs[NewPatient]("new").map(x => "n")
//          val s = mess.getAs[SnapShot]("state").map(x => "s")
//          val r = mess.getAs[RemovedPatient]("removed").map(x => "r")
//
//          d.getOrElse(np.getOrElse(r.getOrElse(s.get)))
//        }
//      }
//
//      import com.github.nscala_time.time.Imports._
//      val temp = manyRemoved.map(kv => kv._1->kv._2.groupBy(identity).mapValues(_.size))
//      val filTemp = temp.filter(_._2.get("r").map(_>1).getOrElse(false))
//
//      println(s"some removed: ${filTemp}")

//
//      val analyse = pats(filTemp.head._1)
//      val analyseSort = analyse.sortWith { (a, b) =>
//        val ta = a.findAs[DateTime]("timestamp") ++ a.findAs[String]("timeStamp").map(new DateTime(_))
//        val tb = b.findAs[DateTime]("timestamp") ++ b.findAs[String]("timeStamp").map(new DateTime(_))
//        ta.head < tb.head
//      }
//
//      val tomte = analyse.flatMap{ mess =>
//        mess.findAs[DateTime]("timestamp") ++ mess.findAs[String]("timeStamp").map(new DateTime(_))
//      }.sortWith(_ < _)
//
//      println(tomte)


//      val remo = pats.foldLeft(List[List[String]]())((a,b) => {
//        b._2.map{ mess =>
//          val d = mess.getAs[PatientDiff]("diff").map(x => "d")
//          val np = mess.getAs[NewPatient]("new").map(x => "n")
//          val s = mess.getAs[SnapShot]("state").map(x => "s")
//          val r = mess.getAs[RemovedPatient]("removed").map(x => "r")
//
//          d.getOrElse(np.getOrElse(r.getOrElse(s.get)))
//
//        } :: a
//      })
//
//      println("list")
//      remo.map(l => println(l.reverse))
//      println("")


//      val kalle = pats.map {
//        case (id, xs) => {
//          val rev = xs.reverse
//          val k = rev.map(_.body.obj.head._1)
//          val list = rev.flatMap(_.find("timestamp")) ++ rev.flatMap(_.find("timeStamp"))
//
//          k zip list
//        }
//      }
//
//      kalle.head.foreach(println)
//      println(pats.head._2.reverse)






//      val k = for {
//        p <- pats
//        mess <- p._2
//        ev1 <- mess.findAs[List[ElvisEvent]]("Events") ++ mess.findAs[List[ElvisEvent]]("newEvents")
//        e <- ev1
//      } yield e
//
//      println(s"EventTypes: ${k.map(_.Type).groupBy(identity).mapValues(_.size)}")
//
//      val y = for {
//        p <- pats
//        mess <- p._2
//        ev1 <- mess.findAs[List[ElvisEvent]]("removedEvents")
//        e <- ev1
//      } yield e
//
//      println(s"removed EventTypes: ${y.map(_.Type).groupBy(identity).mapValues(_.size)}")
//
//
//
//      val z = (for {
//        p <- pats
//        mess <- p._2
//        ev1 <- mess.getAs[PatientDiff]("diff")
//      } yield ev1).flatMap(_.updates.map(_._1))
//
//      println(s"patientUpds: ${z.groupBy(identity).mapValues(_.size)}")

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

