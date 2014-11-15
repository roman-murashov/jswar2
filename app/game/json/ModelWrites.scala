package game.json

import game.{Neutral, Orc, Human, Race}
import models.unit.json.UnitCharacteristicWrites
import play.api.libs.json.{JsString, JsValue, Writes}

trait ModelWrites {
  implicit val raceWrites = new Writes[Race] {
    def writes(o: Race): JsValue = o match {
      case Human => JsString("human")
      case Orc => JsString("orc")
      case Neutral => JsString("neutral")
    }
  }

  implicit val ucWrites = new UnitCharacteristicWrites
}
