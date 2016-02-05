package lisa.endpoint.esb

import akka.actor._
import akka.camel._
import akka.event.Logging
import org.apache.activemq.camel.component.ActiveMQComponent

import lisa.endpoint.message._
import lisa.endpoint.message.MessageLogic._
import org.json4s._
import org.json4s.native.JsonMethods._

case class LISAEndPointProperties(
             endpointName : String,
             consumeTopics : List[String],
             produceTopics : List[String] = List(),
             messageFilter: LISAMessage => Boolean = (_ => true),
             attributes: JObject = JObject(List())
)

object LISAEndPoint {
  def initial(system: ActorSystem) = {
    val conf = com.typesafe.config.ConfigFactory.load.getConfig("lisa.buss")
    val bussIP = conf.getString("ip")
    val bussPort = conf.getString("port")

    println(s"Connecting to buss on ip: $bussIP and port: $bussPort defined in lisa.buss.ip and .port")

    import org.apache.activemq.camel.component.ActiveMQComponent
    val camel = CamelExtension(system)
    camel.context.addComponent("activemq", ActiveMQComponent.activeMQComponent(
      s"tcp://$bussIP:$bussPort"))
  }
}

/**
 * This is the Scala endpoint that you can use as an endpoint for the LISA ESB
 * 
 * To use it the following lines needs to be states when setting up the actor system:
 * 
 * val amqUrl = s"nio://localhost:61616"  // an example. use the activeMQ ip
 * camel.context.addComponent("activemq", ActiveMQComponent.activeMQComponent(amqUrl))
 * 
 * implement the:
 * def receive = {
 *  case LISAMessage(body, header) => ...
 * }
 * 
 */
abstract class LISAEndPoint(prop : LISAEndPointProperties) extends Actor {

  val logg = Logging(context.system, this)
  val epAttributes = {
    val ip = java.net.InetAddress.getLocalHost.getHostAddress
    JObject(prop.attributes.obj ++ List(
      "ip" -> JString(ip),
      "name" -> JString(prop.endpointName),
      "consume" -> JArray(prop.consumeTopics.map(JString.apply)),
      "produce" -> JArray(prop.produceTopics.map(JString.apply))
    ))
  }

     
  //val camel = CamelExtension(context.system)
  private val consumeT = prop.consumeTopics map {(topic) =>
    val c = context.actorOf(Props(classOf[LISAConsumer], "topic:"+ topic))
    c ! Listen(self, prop.messageFilter)
    topic -> c
  } toMap

  //val camel = CamelExtension(context.system)
  private val produceT = {if (prop.produceTopics.isEmpty) prop.consumeTopics else prop.produceTopics} map {(topic) =>
    val p = context.actorOf(Props(classOf[LISAProducer], "topic:"+ topic, epAttributes))
    topic -> p
  } toMap
  
  def sendTo(topicName: String): ActorRef = {
    produceT(topicName)
  }
  
  def topics: ProducerHolder = {
    val p = produceT map (_._2)
    ProducerHolder(p.toList)
  }

  private var tempTopicMap: Map[String, ActorRef] = Map.empty
  /**
   * This sender method is used for producing messages to a topic not 
   * registered during construction of the endpoint
   */
  def produceToTopic(topic: String) = {
    if (!tempTopicMap.contains(topic)){
      val newAttr = epAttributes.transformField{
        case ("produce", _) => "produce" -> JArray(List(JString(topic)))
      }
      tempTopicMap = tempTopicMap + (topic ->context.actorOf(Props(classOf[LISAProducer], "topic:"+ topic, newAttr)))
    }
    tempTopicMap(topic)
  }
  
}

/**
 * Two classes to register for a consumer
 */
case class Listen(ref: ActorRef, messageFilter: (LISAMessage => Boolean) = _ => true)
case class UnListen(ref: ActorRef)


/**
 * Case class to wrap multiple producers to enable sending with !
 */
case class ProducerHolder(producers: List[ActorRef]) {
  def !(l: LISAMessage) = {
    producers foreach (_ ! l)
  }
}
