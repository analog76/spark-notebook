package notebook
package server

import java.io.{File, FileInputStream, FileWriter}

import scala.concurrent._
import scala.concurrent.duration._

import akka.actor._

import play.api.{Configuration, Logger}
import play.api.libs.json._

class NotebookClusters(store:File, initClusters: Map[String, JsObject]) extends Actor with ActorLogging {
  import NotebookClusters._

  var clusters:Map[String, JsObject] = initClusters

  def receive = {
    case Add(name, o)    =>
      clusters = clusters + (name → o)
      dump()
      sender ! o

    case Remove(name, o) =>
      clusters = clusters - name
      dump()

    case Get(name)       =>
      sender ! clusters.get(name)

    case All             =>
      sender ! clusters.values.toList
  }

  def dump() {
    val j = Json.prettyPrint(JsObject(clusters.toSeq))
    val w = new FileWriter(store, false)
    try {
      w.write(j)
    } finally {
      w.close
    }
  }
}

object NotebookClusters {
  case class Add(name:String, o:JsObject)
  case class Remove(name:String, o:JsObject)
  case class Get(name:String)
  case object All

  def apply(config: Configuration):NotebookClusters = {
    val clustersFile = config.getString("file").map(new File(_)).getOrElse(new File("./conf/clusters"))

    val source = scala.io.Source.fromFile(clustersFile)
    val lines = source.mkString
    source.close()

    val j = Json.parse(lines)
    val init = j match {
      case JsArray(xs)     =>
        val v = xs.map(x => ((x \ "name").as[String], x)).toMap
        val m = v.collect{case x@(_, o:JsObject) => x}.toMap.asInstanceOf[Map[String, JsObject]]
        if (m.size != v.size) {
          Logger.warn("Some items have been discarded from clusters → no Json Objects!")
        }
        m

      case o@JsObject(xs)  =>
        val v = o.value
        val m = v.collect{case x@(_, o:JsObject) => x}.toMap.asInstanceOf[Map[String, JsObject]]
        if (m.size != v.size) {
          Logger.warn("Some items have been discarded from clusters → no Json Objects!")
        }
        m
      case x               => throw new IllegalStateException("Cannot load clusters got: " + x)
    }
    new NotebookClusters(clustersFile, init)
  }
}