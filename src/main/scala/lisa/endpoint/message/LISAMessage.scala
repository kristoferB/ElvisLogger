package lisa.endpoint.message

import org.joda.time.DateTime
import org.json4s._

import scala.util.Try

case class LISAMessage(body: JObject, header: Map[String, Any] = Map())

case object LISAMessage {
  def apply[T](pair: (String, T)*)(implicit formats : org.json4s.Formats, mf : scala.reflect.Manifest[T]): LISAMessage = {
    val res = pair.map{
      case (key, value) => key -> Extraction.decompose(value)
    }
    LISAMessage(JObject(res.toList))
  }

  def body[T](pair: (String, T)*)(implicit formats : org.json4s.Formats, mf : scala.reflect.Manifest[T]): JObject = {
    val res = pair.map{
      case (key, value) => key -> Extraction.decompose(value)
    }
    JObject(res.toList)
  }

  def fromJson(str: String) = {
    val body = bodyFromJson(str)
    import org.json4s.native.JsonMethods._
    val json = Try(parse(str))
    json.flatMap{ v =>
      val h = v \ "header" match {
        case x: JObject => x
        case x => JObject()
      }
      body.map{
        case x : JObject => LISAMessage(x,h.obj.toMap)
        case x => LISAMessage(JObject("key"->x), h.obj.toMap)
      }
    }
  }

  def bodyFromJson(str: String) = {
    import org.json4s.native.JsonMethods._
    val json = Try(parse(str))
    json.map{ v =>
      v \ "body" match {
        case x: JObject => x
        case x => v
      }
    }
  }

  def newID = {
    java.util.UUID.randomUUID.toString
  }
}

object MessageLogic {

  trait LISAFormats extends DefaultFormats {
    override val typeHintFieldName = "isa"
    override val customSerializers: List[Serializer[_]] = org.json4s.ext.JodaTimeSerializers.all :+ org.json4s.ext.UUIDSerializer
    override val dateFormatter = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  }
  implicit val format = new LISAFormats {}

  def timeStamp = {
    Extraction.decompose(org.joda.time.DateTime.now)
  }

  implicit class extendsJson(json: JValue) {
    def getAs[T](implicit formats : org.json4s.Formats, mf : scala.reflect.Manifest[T]) = {
      tryWithOption(
        json.extract[T]
      )
    }
  }


  implicit class messLogic(mess: LISAMessage) {

    def +[T](kv: (String, T)*)(implicit formats : org.json4s.Formats, mf : scala.reflect.Manifest[T]) = {
      val j = kv map(x => x._1 -> Extraction.decompose(x._2))
      mess.copy(body = mess.body.copy(obj = mess.body.obj ++ j))
    }
    def +(xs: JObject) = {
      mess.copy(body = mess.body.copy(obj = mess.body.obj ++ xs.obj))
    }
    def addHeader(kv: (String, Any)) = {
      mess.copy(header = mess.header + kv)
    }


    def get(key: String) = {
      mess.body \ key
    }

    def getAs[T](implicit formats : org.json4s.Formats, mf : scala.reflect.Manifest[T]) = {
      tryWithOption(
        mess.body.extract[T]
      )
    }

    def getAs[T](key: String)(implicit formats : org.json4s.Formats, mf : scala.reflect.Manifest[T]) = {
      val res = mess.body \ key
      tryWithOption(
        res.extract[T]
      )
    }
    def find(key: String) = mess.body \\ key match {
      case JObject(xs) => xs.map(_._2)
      case x: JValue => List(x)
    }
    def findAs[T](key: String)(implicit formats : org.json4s.Formats, mf : scala.reflect.Manifest[T]) = {
      for {
        x <- find(key)
        v <- tryWithOption(x.extract[T])
      } yield v
    }
    def findObjectsWithKeys(keys: List[String]) = {
      mess.body.filterField {
        case JField(key, JObject(xs)) => {
          val inObj = xs.map(_._1).toSet
          keys.forall(inObj contains)
        }
        case _ => false
      }
    }
    def findObjectsWithKeysAs[T](keys: List[String])(implicit formats : org.json4s.Formats, mf : scala.reflect.Manifest[T]) = {
      for {
        value <- findObjectsWithKeys(keys)
        t <- tryWithOption(value._2.extract[T])
      } yield (value._1, t)
    }
    def findObjectsWithFields(fields: List[JField]) = {
      mess.body.filterField {
        case JField(key, JObject(xs)) => {
          fields.forall(xs contains)
        }
        case _ => false
      }
    }
    def findObjectsWithFieldsAs[T](fields: List[JField])(implicit formats : org.json4s.Formats, mf : scala.reflect.Manifest[T]) = {
      for {
        value <- findObjectsWithFields(fields)
        t <- tryWithOption(value._2.extract[T])
      } yield (value._1, t)
    }
    def getTopic: String = {
      tryWithOption(mess.header("JMSDestination").asInstanceOf[javax.jms.Topic]) match {
        case Some(t) => t.getTopicName()
        case None => ""
      }
    }
    def getHeaderTime: Option[DateTime] = {
      tryWithOption(mess.header("JMSTimestamp").asInstanceOf[Long]) map { t =>
        new org.joda.time.DateTime(new java.util.Date(t))
      }
    }
    def contains(key: String) = {
      !find(key).isEmpty
    }

    import org.json4s.native.JsonMethods._
    def toJson = {
      val hs = mess.header.map{
        case (key, value) => key -> JString(value.toString)
      }
      val hsJson = JObject(hs.toList)
      compact(render(JObject("header"-> hsJson, "body"->mess.body)))

    }
    def bodyToJson = {
      compact(render(mess.body))
    }
    def headerToJson = {
      val hs = mess.header.map{
        case (key, value) => key -> JString(value.toString)
      }
      compact(render(JObject(hs.toList)))
    }
  }


  def tryWithOption[T](t: => T): Option[T] = {
    try {
      Some(t)
    } catch {
      case e: Exception => None
    }
  }
}





//object DatePrimitive {
//  def stringToDate(s: String, pattern: String = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ"): Option[DateTime] = {
//     val fmt = org.joda.time.format.DateTimeFormat.forPattern(pattern)
//     try
//       Some(fmt.parseDateTime(s))
//     catch {
//       case e:Exception => None
//     }
//  }
//  //import org.json4s.native.Serialization.{read, write}
//  implicit val formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all
//
//  def now = Extraction.decompose(DateTime.now)
//}
