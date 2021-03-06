package notebook.front

import play.api.libs.json._

import notebook._, JSBus._
import notebook.JsonCodec._
import notebook.front.widgets._

trait JsWorld[I, O] extends Widget with IODataConnector[I, O] {

  def data: Seq[I]
  def scripts: List[Script]
  def snippets:List[String]=Nil

  lazy val json = JsonCodec.tSeq[O].decode(toO(data))

  private lazy val js = ("playground" :: scripts.map(_.script)).map(x => s"'../javascripts/notebook/$x'").mkString("[", ",", "]")
  private lazy val call =
    s"""
      function(playground, ${scripts.map(_.name).mkString(", ")}) {
        // data ==> data-this (in observable.js's scopedEval) ==> this in JS => { dataId, dataInit, ... }
        // this ==> scope (in observable.js's scopedEval) ==> this.parentElement ==> div.container below (toHtml)

        playground.call(data,
                        this
                        ${ if (scripts.size>0) "," else "" }
                        ${scripts.map(s => s.toJson).mkString(", ")}
                        ${ if (snippets.size>0) "," else "" }
                        ${snippets.mkString(", ")}
                      );
      }
    """

  lazy val toHtml =
    <div class="container">
    {
      scopedScript(
        s"req($js, $call);",
        Json.obj(
          "dataId" -> dataConnection.id,
          "dataInit" -> json
        )
      )
    } </div>


}

class Playground[T] (
    override val data: Seq[T],
    override val scripts: List[Script]=Nil,
    override val snippets:List[String]=Nil
  )(implicit val singleCodec:Codec[JsValue, T]) extends JsWorld[T, T] {
  override lazy val toO = identity[Seq[T]] _
  val singleToO = identity[T] _
}