package game.unit

import game.world.World
import game.{Stop, Order}

trait AtomicAction {
  val ticksLeft: Int
  val unit: Unit
  val order: Order
  def spentTick(world: World, rest: Unit#ActionsType): Set[_ >: Change]

  // change don't include order changes
  def isChanged(aa: AtomicAction): Boolean
  def change: Set[(String, String)]
}

case class Still(unit: Unit) extends AtomicAction {
  val ticksLeft = 1
  val order = Stop

  def spentTick(world: World, rest: Unit#ActionsType): Set[_ >: Change] = rest match {
    case x :: xs => Set(UnitActionsChange(unit, unit.atomicAction.tail))
    case Nil => Set()
  }

  def isChanged(aa: AtomicAction): Boolean = aa match {
    case Still(_) => false
    case _ => true
  }

  def change = Set(("action", "still"))
}

/* precompute A* if not precomputed and if we reach non-empty field - we compute it again */
case class Move(x: Int, y: Int, unit: Unit, order: Order, ticksLeft: Int, ticksOverall: Int) extends AtomicAction {
  require(ticksLeft > 0)

  def spentTick(world: World, rest: Unit#ActionsType): Set[_ >: Change] = {
    assert(ticksLeft > 0)

    if (ticksLeft == ticksOverall) {
      world.unitsOnMap(y)(x) match {
        case Some(_) => Set(UnitActionsChange(unit, Still(unit) :: rest))
        case None =>
          Set(UnitOccupyCell(unit, (x, y)), UnitActionsChange(unit, Move(x, y, unit, order, ticksLeft - 1, ticksOverall) :: rest))
      }
    } else {
      // check invariant
      assert(world.unitsOnMap(y)(x) == Some(unit))

      var nextMoves = rest
      if (ticksLeft > 1)
        nextMoves = Move(x, y, unit, order, ticksLeft - 1, ticksOverall) :: nextMoves

      // unit leave its previous cell
      if (ticksLeft == ticksOverall / 2) {
        Set(UnitPositionChange(unit, (x, y)), UnitActionsChange(unit, nextMoves))
      } else Set(UnitActionsChange(unit, nextMoves))
    }
  }

  // change don't include order changes
  def isChanged(aa: AtomicAction): Boolean = aa match {
    case Move(_x, _y, _, _, _, _) => _x != x || _y != y
    case _ => true
  }

  def change: Set[(String, String)] = Set(("action", "move"), ("moveX", String.valueOf(x)), ("moveY", String.valueOf(y)))
}

case class Attack(unit: Unit, order: Order, ticksLeft: Int) extends AtomicAction {
  def spentTick(world: World, rest: Unit#ActionsType): Set[_ >: Change] = ???

  // change don't include order changes
  def isChanged(aa: AtomicAction): Boolean = ???

  def change: Set[(String, String)] = ???
}