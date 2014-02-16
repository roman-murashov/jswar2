package game.unit

import format.pud.PudCodec
import models.unit.UnitCharacteristic
import game.{Orc, Human, Neutral, Race, Order}
import game.world.World
import play.Logger

class Unit(val id: Int, pudUnit: PudCodec.Unit, val name: String, val ch: UnitCharacteristic) {
  var x = pudUnit.x
  var y = pudUnit.y
  val player = pudUnit.player
  val data = pudUnit.data
  val isBuilding: Boolean = pudUnit.Type > 58

  val width = ch.unitSize._1
  val height = ch.unitSize._2

  var hp = ch.hitPoints
  var armor = ch.armor

  var atomicAction: Iterator[AtomicAction] = Iterator.continually(Still(this))
  var order: Option[Order] = None

  def spentTick(world: World) {
    Logger.debug("spentTick on " + this.id)
    if (atomicAction.hasNext) {
      val nextAction = atomicAction.next()
      val nextActions = nextAction.spentTick(world)

      val nonEmptyActions = nextActions.filter(_.ticksLeft != 0)

      atomicAction = nonEmptyActions.toIterator ++ atomicAction
    } else {
      Logger.warn(s"AtomicAction iterator is empty for user $x, $y")
    }
  }

  def peekAtomicAction: Option[AtomicAction] = {
    Logger.debug("peekAtomicAction on " + this.id)
    if (atomicAction.hasNext) {
      val next = atomicAction.next()
      atomicAction = Iterator.single(next) ++ atomicAction
      Some(next)
    } else None
  }

//  def actions: MultiMap[(Action, Option[Class[_ <: Unit[T]]]), Option[ActionParam]] =
//    new MultiMap(Map[(Action, Option[Class[_ <: Unit[T]]]), Set[Option[ActionParam]]]())

  /** in original warcraft 2 sight center of unit appears at bottom right center of unit sprite */
  def centerCoords = ((x + width) * 32 - 16, (y + height) * 32 - 16)
}

object Unit {
  var counter: Int = 0

  def apply(pudUnit: PudCodec.Unit, race: Race, unitTypes: Vector[(String, UnitCharacteristic)]): Unit = {
    val u = race match {
      // human units are odd, orc - even
      // care: pudUnit.Type not changed
      case Human => if (pudUnit.Type % 2 == 0) new Unit(counter, pudUnit, unitTypes(pudUnit.Type)._1, unitTypes(pudUnit.Type)._2)
      else new Unit(counter, pudUnit, unitTypes(pudUnit.Type - 1)._1, unitTypes(pudUnit.Type - 1)._2)
      case Orc => if (pudUnit.Type % 2 == 1) new Unit(counter, pudUnit, unitTypes(pudUnit.Type)._1, unitTypes(pudUnit.Type)._2)
      else new Unit(counter, pudUnit, unitTypes(pudUnit.Type + 1)._1, unitTypes(pudUnit.Type + 1)._2)
      case Neutral => new Unit(counter, pudUnit, unitTypes(pudUnit.Type)._1, unitTypes(pudUnit.Type)._2)
    }
    counter += 1
    u
  }
}