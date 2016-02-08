package sekvensa.logging

import akka.actor._
import com.codemettle.reactivemq._
import com.codemettle.reactivemq.ReActiveMQMessages._
import com.codemettle.reactivemq.model._

/**
 * Created by kristofer on 15-05-17.
 */
object ElvisToEVAH extends App {
  val system = ActorSystem("system")
  val a = system.actorOf(Props[AnActor])
  val a2 = system.actorOf(Props[AnActor2])

  a ! "connect"
  a2 ! "connect"



}

class AnActor extends Actor {

  def receive = {
    case "connect" => {
      //ReActiveMQExtension(context.system).manager ! GetConnection("nio://localhost:61616", Some("myName"))
      ReActiveMQExtension(context.system).manager ! GetAuthenticatedConnection("nio://localhost:61616", "user", "pass")
    }
    case ConnectionEstablished(request, c) => {
      println("connected:"+request)
      c ! ConsumeFromTopic("elvisSnapShot")
    }
    case ConnectionFailed(request, reason) => {
      println("failed:"+reason)
    }
    case mess @ AMQMessage(body, prop, headers) => {
      println(mess)
    }
  }
}


class AnActor2 extends Actor {

  def receive = {
    case "connect" => {
      //ReActiveMQExtension(context.system).manager ! GetConnection("nio://localhost:61616", Some("myName"))
      ReActiveMQExtension(context.system).manager ! GetAuthenticatedConnection("nio://localhost:61616", "user", "pass")
    }
    case ConnectionEstablished(request, c) => {
      println("connected:"+request)
      c ! SendMessage(Topic("t1"), AMQMessage("hej från mig"))
      c ! ConsumeFromTopic("elvisPlayBack")
    }
    case ConnectionFailed(request, reason) => {
      println("failed:"+reason)
    }
    case mess @ AMQMessage(body, prop, headers) => {
      println(s"play: $mess")
    }
  }
}