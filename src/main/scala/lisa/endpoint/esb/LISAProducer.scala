package lisa.endpoint.esb

import akka.actor._
import akka.camel._
import akka.event.Logging
import org.json4s._
import org.json4s.native.JsonMethods._

import lisa.endpoint.message._

class LISAProducer(topic: String, endpointInfo: JObject) extends Actor with Producer with Oneway{
  def endpointUri = "activemq:"+topic
  val log = Logging(context.system, this)

  override def transformOutgoingMessage(msg: Any) = {
    val cm = msg match {
      case msg: CamelMessage => msg
      case LISAMessage(body, header) => CamelMessage(compact(render(body)), header)
      case k => CamelMessage(k, Map())
    }
    val t = addHistory(cm)
    t
  }

  def addHistory(msg: CamelMessage) = {
    val history = msg.headers.get("LISAHistory") match {
      case Some(JArray(xs)) => JArray(endpointInfo :: xs)
      case Some(x: JValue) => JArray(List(endpointInfo, x))
      case x => JArray(List(endpointInfo))
    }
    CamelMessage(msg.body, msg.headers.+("LISAHistory"->compact(render(history))))
  }

}

object LISAProducer{
  def prop(topic: String) = Props(classOf[LISAProducer], "topic:"+ topic)
}


