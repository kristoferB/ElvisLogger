package lisa.endpoint.examples

import lisa.endpoint.message._

//import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL.WithDouble._
import lisa.endpoint.esb._



/**
 * This is an example of how to implement an endpoint. 
 */
class ExampleEndPoint(prop : LISAEndPointProperties) extends LISAEndPoint(prop) {
  // Will consume from all topics in prop.topics
  def receive = {
    case mess: LISAMessage => {
      import lisa.endpoint.message.MessageLogic._
      val topic = mess.getTopic // The topic the message was sent to

      val updatedMessage1 = mess + ("newAttribute" -> 1) ~ ("newAttribute2" -> 1)
      val updatedMessage2 = updatedMessage1 + ("newAttribute3" -> 1) addHeader("headerInfo"->"kalle")


      // Examples
      //sendTo("test") ! mess		// sends message back to a specific topic. throws if topic is not defined
      topics ! updatedMessage1 + ("time" -> MessageLogic.timeStamp)		// send mess back to all topics
    }
  }
}
