package lisa.endpoint.esb

import akka.actor._
import akka.camel._
import akka.actor.Status.Failure
import akka.event.Logging
import org.json4s._
import org.json4s.native.JsonMethods._

import lisa.endpoint.message._



class LISAConsumer(topic: String) extends Actor with Consumer {
  def endpointUri = "activemq:"+topic
  
  val log = Logging(context.system, this)
  
  log.debug("Creating consumer " + LISAConsumer.this)
  log.debug("Creating consumer on" + topic)
    
  
  private var listeners: Set[Listen] = Set()
  
  def receive = {
    case msg: CamelMessage => {
      log.debug("recevied this camelMessage: "+msg)
      msg.body match {
        case lb: String => {
          log.debug(s"Consumer ${self} got a message: $lb")
          val header = parseHeader(msg.headers)

          tryWithOption(parse(lb)) match {
            case Some(json: JObject) =>
              val lisaMessage = LISAMessage(json, header)
              toListner(lisaMessage)
            case Some(json: JValue) => {
              val lisaMessage = LISAMessage(JObject(List("nokey"->json)), header)
              toListner(lisaMessage)
            }
            case None => toListnerError(msg)
          }
        }
        case x => {
          toListnerError(msg)
        }
      }
    }
    case r: Listen => listeners = listeners + r
    case UnListen(r) => listeners = listeners.filter(_.ref != r)
  }

  def parseHeader(headers: Map[String, Any]): Map[String, Any] = {
    val h = (for {
      h <- tryWithOption(headers("LISAHistory").asInstanceOf[String])
      list <- tryWithOption(parse(h))
    } yield list).getOrElse(JArray(List()))
    headers.filter(_._2 != null).+("LISAHistory"-> h)
  }

  def toListner(mess: LISAMessage) = listeners foreach {(listner)=>
      if (listner.messageFilter(mess))
        listner.ref ! mess
  }
  def toListnerError(f: Any) = listeners foreach {(listner)=>
    log.debug("Didn't recevied a LISAMessage: "+f)
    listner.ref ! Failure(new Exception("Message " + f + " is not a LISAMessage"))
  }


  def tryWithOption[T](t: => T): Option[T] = {
    try {
      Some(t)
    } catch {
      case e: Exception => None
    }
  }
}

object LISAConsumer{
  def prop(topic: String) = Props(classOf[LISAConsumer], "topic:"+ topic)
}