package services.cqrs.domain

import java.time.Instant
import services.cqrs.CborSerializable
import services.cqrs.domain.ShoppingCart._

sealed trait ShoppingCart {
  def cartId: String

  def items: Map[String, Int]

  def handleEvent(event: ShoppingCart.Event): ShoppingCart = this
}

object ShoppingCart {
  sealed trait Event extends CborSerializable {
    def cartId: String
  }

  final case class ItemAdded(cartId: String, itemId: String, quantity: Int)
      extends Event

  final case class ItemRemoved(cartId: String, itemId: String) extends Event

  final case class ItemQuantityAdjusted(
      cartId: String,
      itemId: String,
      newQuantity: Int
  ) extends Event

  final case class CheckedOut(cartId: String, eventTime: Instant) extends Event

  def empty(cartId: String) = OpenShoppingCart(cartId, Map.empty)
}

case class OpenShoppingCart(cartId: String, items: Map[String, Int])
    extends ShoppingCart {

  def addItem(id: String, quantity: Int) =
    if (quantity < 1) {
      Left("Quantity must be greater than 0")
    } else if (items.contains(id)) {
      Left(s"Item $id already added to basket")
    } else {
      Right(ItemAdded(cartId, id, quantity))
    }

  def removeItem(id: String) =
    if (!items.contains(id)) {
      None
    } else {
      Some(ItemRemoved(cartId, id))
    }

  def adjustQuantity(id: String, quantity: Int) =
    if (quantity < 1) {
      Left("Quantity must be greater than 0")
    } else if (!items.contains(id)) {
      Left(s"Item $id already not in basket")
    } else if (items.get(id).map(_ == quantity).getOrElse(false)) {
      Left(s"Item $id quantity is already $quantity")
    } else {
      Right(ItemQuantityAdjusted(cartId, id, quantity))
    }

  def checkout() =
    if (items.isEmpty) {
      Left("Cannot checkout empty shopping cart")
    } else {
      Right(CheckedOut(cartId, Instant.now()))
    }

  override def handleEvent(event: ShoppingCart.Event): ShoppingCart =
    event match {
      case ItemAdded(cartId, itemId, quantity) =>
        OpenShoppingCart(cartId, items + (itemId -> quantity))
      case ItemRemoved(cartId, itemId) =>
        OpenShoppingCart(cartId, items - itemId)
      case ItemQuantityAdjusted(cartId, itemId, newQuantity) =>
        OpenShoppingCart(cartId, items + (itemId -> newQuantity))
      case CheckedOut(cartId, eventTime) =>
        CheckedOutShoppingCart(cartId, items, eventTime)
    }
}

case class CheckedOutShoppingCart(
    cartId: String,
    items: Map[String, Int],
    checkoutOutAt: Instant
) extends ShoppingCart
