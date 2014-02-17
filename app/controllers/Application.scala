package controllers

import _root_.format.pud.Pud
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import java.io.File
import se.radley.plugin.enumeration.form._
import play.api.libs.json._
import akka.actor.{Props, ActorRef}
import play.api.libs.iteratee.{Iteratee, Enumerator, Concurrent}
import akka.pattern.ask
import play.api.libs.concurrent.Akka
import game._
import game.GameActor.{PlayerClientInitOk, PlayerWebSocketInitOk, GameCreated, NewGame}
import play.api.Play.current
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import play.Logger
import concurrent.Promise
import play.api.libs.iteratee.Concurrent.Channel
import game.PlayerActor.{InitOk, DoAction}
import game.ControlledPlayerActor.WebSocketInitOk

object Application extends Controller {
  implicit val timeout = akka.util.Timeout(5 second)

  lazy val puds: Map[String, Pud] = new File("conf/maps/multi").listFiles()
    .flatMap(f => Pud(f.getAbsolutePath) map { p => (f.getName -> p) } ).toMap

  var games = Map[Int, ActorRef]()
  var counter = 0

  def pudsWithDescription = puds.toSeq map { p => (p._1, p._2.description) }
  def jsonPlayerSlots = Json.toJson(puds map { p => (p._1, p._2.players map { _.num }) }).toString()

  def index = Action {
    Ok(views.html.newGame(newGameForm, pudsWithDescription, jsonPlayerSlots))
  }

  val singlePlayerForm = Form(
    mapping(
      "race" -> enum(controllers.Race),
      "resources" -> enum(Resources),
      "units" -> enum(Units),
      "opponents" -> enum(Opponents),
      "tileset" -> enum(Tileset),
      "mapFileName" -> nonEmptyText.verifying("Map not found", new File(_).exists())
    )(SinglePlayerSetting.apply)(SinglePlayerSetting.unapply)
  )

  val newGameForm = Form(
    tuple(
      "pudFileName" -> text,
      "tileset" -> enum(Tileset),
      "peasantOnly" -> boolean
    )
  )

//  def singlePlayerGame = Action { implicit request =>
//    singlePlayerForm.bindFromRequest.fold(
//      formWithErrors => BadRequest(views.html.newSinglePlayer(formWithErrors)),
//      value => {
////        Pud(value.mapFileName).fold(
////          e => NotFound(e),
////          pud => Ok(views.html.game(new UnitFeatures)(value)(pud))
////        )
//        Ok()
//      }
//    )
//  }

  def createGame = Action { implicit request =>
    newGameForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.newGame(formWithErrors, pudsWithDescription, jsonPlayerSlots)),
      value => {
        val (pudFileName, tileset, peasantOnly) = value
        val gameSettings = GameSettings(0, Map(0 -> PlayerSettings(Orc), 6 -> PlayerSettings(Orc)), peasantOnly)

        val gameActor = Akka.system.actorOf(Props(new GameActor()))

        val currentCounter = counter

        (gameActor ? NewGame(puds(pudFileName), gameSettings)).foreach({
          case GameCreated => Logger.info(s"Created game$currentCounter")
        })

        games += currentCounter -> gameActor

        counter += 1
        Ok(views.html.game(currentCounter, gameSettings.controlledPlayerNo, tileset, puds(pudFileName)))
      }
    )
  }

  def ws(gameId: Int, playerId: Int) = WebSocket.using[JsValue] { request =>
    val (enumerator, channel) = Concurrent.broadcast[JsValue]

    val in = Iteratee.foreach[JsValue](event => {
      implicit val orderReads = new Reads[Order] {
        def reads(json: JsValue): JsResult[Order] = (json \ "name").as[String] match {
          case "move" => Json.reads[Move].reads(json)
        }
      }

      implicit val tuple2reads = new Reads[(Int, Order)] {
        def reads(json: JsValue): JsResult[(Int, Order)] =
          JsSuccess((Json.fromJson[Int](json \ "unit").get, Json.fromJson[Order](json).get))
      }

      Logger.debug(s"Received json event: $event")

      (event \ "type").as[String] match {
        case "WebSocketInitOk" => games(gameId) ! PlayerWebSocketInitOk(playerId, channel)
        case "ClientInitOk" => games(gameId) ! PlayerClientInitOk(playerId)
        case _ => Json.fromJson[List[(Int, game.Order)]](event \ "actionEvents").fold[Unit](
          _.foreach(p => Logger.error(p._1 + " " + p._2)),
          obj => games(gameId) ! DoAction(obj)
        )
      }
    })

    (in, enumerator)
  }
}