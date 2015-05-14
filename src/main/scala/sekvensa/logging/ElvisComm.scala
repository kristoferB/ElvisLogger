package sekvensa.logging

import akka.actor._
import scala.concurrent.duration._;
import scala.concurrent._
import sekvensa.elvis._


class ElvisComm(sendTo: ActorRef) extends Actor with ActorLogging {

  val service = (new BasicHttpBinding_IDepartmentListBindings with
    scalaxb.Soap11ClientsAsync with
    scalaxb.DispatchHttpClientsAsync {}).service

  var terminate = false


  def receive = {
  	case "GET" => {
      //println("GETTING")
      val fresponse = service.fetchDepartmentListWithEvents(Some(Some(ArrayOfstring(Some("")))))
      val patients = getPatients(fresponse)
      patients map (l => sendTo ! SnapShot(l))

      patients.onFailure{
        case t => println(s"Error in comm: ${t.getMessage}")
      }
//      fresponse.map{r =>
//        for {
//          resp <- r.FetchDepartmentListWithEventsResult.get
//        } yield {
//          println(s"RESP: ${resp.Message}, ${resp.ResultCode}")
//        }
//      }
      if (!terminate)
        context.system.scheduler.scheduleOnce(10 seconds, self, "GET")
    }
    case "TERMINATE" => terminate = true

  }

  def getPatients(x: Future[FetchDepartmentListWithEventsResponse]): Future[List[ElvisPatient]] = {
    x map{ resp => {
      for {
        respType <- resp.FetchDepartmentListWithEventsResult.getOrElse(None)
        dep <- respType.DepartmentList.getOrElse(None)
      } yield {
        for {
          pO <- dep.DepartmentListItemWithEvents
          pat <- pO
          p <- getPatient(pat)
        } yield {
          p
        }
      }
    }.getOrElse(Seq()).toList }
  }



  def getPatient(x: DepartmentListItemWithEvents): Option[ElvisPatient] = {
    for {
      id <- x.CareContactId
      regTime <- x.CareContactRegistrationTime
      comment <- x.DepartmentComment.getOrElse(None)
      events <- x.Events.getOrElse(None)
      loc <- x.Location.getOrElse(None)
      patID <- x.PatientId
      reason <- x.ReasonForVisit.getOrElse(None)
      team <- x.Team.getOrElse(None)
      visitID <- x.VisitId
      visitRegTime <- x.VisitRegistrationTime
    } yield {

      val evs = for {
        oE <- events.Event
        e <- oE
        eID <- e.CareEventId
        eCat <- e.Category.getOrElse(None)
        estart <- e.Start
        eend <- e.End
        etitle <- e.Title.getOrElse(None)
        eType <- e.Type.getOrElse(None)
        eVal <- e.Value.getOrElse(None)
        eVisId <- e.VisitId
      } yield {
        ElvisEvent(eID, eCat, tT(eend), tT(estart), etitle, eType, eVal, eVisId)
      }

      ElvisPatient(id, tT(regTime), comment, evs.toList, loc, patID, reason, team, visitID, tT(visitRegTime))
    }
  }

  import com.github.nscala_time.time.Imports._

  def tT(t: javax.xml.datatype.XMLGregorianCalendar): DateTime = {
    new DateTime(t.toGregorianCalendar)
  }

}

object ElvisComm {
  def props(sendTo: ActorRef) = Props(classOf[ElvisComm], sendTo)


}