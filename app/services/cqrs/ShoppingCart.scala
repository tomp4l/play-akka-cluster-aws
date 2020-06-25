package services.cqrs

import java.time.Instant

import scala.concurrent.duration._

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.RetentionCriteria
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.ReplyEffect

import services.cqrs.domain.{ShoppingCart => ShoppingCartDomain}
import services.cqrs.domain.OpenShoppingCart
import services.cqrs.domain.CheckedOutShoppingCart
import services.cqrs.ShoppingCart.AddItem
import services.cqrs.ShoppingCart.RemoveItem
import services.cqrs.ShoppingCart.AdjustItemQuantity
import services.cqrs.ShoppingCart.Checkout
import services.cqrs.ShoppingCart.Get

/**
  * This is an event sourced actor. It has a state, [[ShoppingCart.State]], which
  * stores the current shopping cart items and whether it's checked out.
  *
  * Event sourced actors are interacted with by sending them commands,
  * see classes implementing [[ShoppingCart.Command]].
  *
  * Commands get translated to events, see classes implementing [[ShoppingCart.Event]].
  * It's the events that get persisted by the entity. Each event will have an event handler
  * registered for it, and an event handler updates the current state based on the event.
  * This will be done when the event is first created, and it will also be done when the entity is
  * loaded from the database - each event will be replayed to recreate the state
  * of the entity.
  */
object ShoppingCart {

  /**
    * This interface defines all the commands that the ShoppingCart persistent actor supports.
    */
  sealed trait Command extends CborSerializable

  type Event = ShoppingCartDomain.Event
  type State = ShoppingCartDomain

  implicit class ToSummary(state: State) {
    def toSummary = state match {
      case OpenShoppingCart(cartId, items) => Summary(items, false)
      case CheckedOutShoppingCart(cartId, items, checkoutOutAt) =>
        Summary(items, true)
    }
  }

  /**
    * A command to add an item to the cart.
    *
    * It can reply with `Confirmation`, which is sent back to the caller when
    * all the events emitted by this command are successfully persisted.
    */
  final case class AddItem(
      itemId: String,
      quantity: Int,
      replyTo: ActorRef[Confirmation]
  ) extends Command

  /**
    * A command to remove an item from the cart.
    */
  final case class RemoveItem(itemId: String, replyTo: ActorRef[Confirmation])
      extends Command

  /**
    * A command to adjust the quantity of an item in the cart.
    */
  final case class AdjustItemQuantity(
      itemId: String,
      quantity: Int,
      replyTo: ActorRef[Confirmation]
  ) extends Command

  /**
    * A command to checkout the shopping cart.
    */
  final case class Checkout(replyTo: ActorRef[Confirmation]) extends Command

  /**
    * A command to get the current state of the shopping cart.
    */
  final case class Get(replyTo: ActorRef[Summary]) extends Command

  /**
    * Summary of the shopping cart state, used in reply messages.
    */
  final case class Summary(items: Map[String, Int], checkedOut: Boolean)
      extends CborSerializable

  sealed trait Confirmation extends CborSerializable

  final case class Accepted(summary: Summary) extends Confirmation

  final case class Rejected(reason: String) extends Confirmation

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("ShoppingCart")

  def init(
      system: ActorSystem[_],
      eventProcessorSettings: EventProcessorSettings
  ): Unit = {
    ClusterSharding(system).init(Entity(EntityKey) { entityContext =>
      val n = math.abs(
        entityContext.entityId.hashCode % eventProcessorSettings.parallelism
      )
      val eventProcessorTag = eventProcessorSettings.tagPrefix + "-" + n
      ShoppingCart(entityContext.entityId, Set(eventProcessorTag))
    }.withRole("write-model"))
  }

  def apply(
      cartId: String,
      eventProcessorTags: Set[String]
  ): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[
        Command,
        ShoppingCartDomain.Event,
        ShoppingCartDomain
      ](
        PersistenceId(EntityKey.name, cartId),
        ShoppingCartDomain.empty(cartId),
        (state, command) =>
          state match {
            case open: OpenShoppingCart             => openShoppingCart(open, command)
            case checkedOut: CheckedOutShoppingCart => checkedOutShoppingCart(checkedOut, command)
          },
        (state, event) => state.handleEvent(event)
      )
      .withTagger(_ => eventProcessorTags)
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1)
      )
  }

  private def openShoppingCart(
      cart: OpenShoppingCart,
      command: Command
  ): ReplyEffect[Event, State] =
    command match {
      case AddItem(itemId, quantity, replyTo) =>
        cart.addItem(itemId, quantity) match {
          case Left(error) =>
            Effect.reply(replyTo)(
              Rejected(error)
            )
          case Right(event) =>
            Effect
              .persist(event)
              .thenReply(replyTo)(updatedCart =>
                Accepted(updatedCart.toSummary)
              )
        }
      case RemoveItem(itemId, replyTo) =>
        cart.removeItem(itemId) match {
          case None =>
            Effect.reply(replyTo)(
              Accepted(cart.toSummary)
            )
          case Some(event) =>
            Effect
              .persist(event)
              .thenReply(replyTo)(updatedCart =>
                Accepted(updatedCart.toSummary)
              )
        }

      case AdjustItemQuantity(itemId, quantity, replyTo) =>
        cart.adjustQuantity(itemId, quantity) match {
          case Left(error) =>
            Effect.reply(replyTo)(
              Rejected(error)
            )
          case Right(event) =>
            Effect
              .persist(event)
              .thenReply(replyTo)(updatedCart =>
                Accepted(updatedCart.toSummary)
              )
        }

      case Checkout(replyTo) =>
        cart.checkout() match {
          case Left(error) =>
            Effect.reply(replyTo)(
              Rejected(error)
            )
          case Right(event) =>
            Effect
              .persist(event)
              .thenReply(replyTo)(updatedCart =>
                Accepted(updatedCart.toSummary)
              )
        }

      case Get(replyTo) =>
        Effect.reply(replyTo)(cart.toSummary)
    }

  private def checkedOutShoppingCart(
      cart: CheckedOutShoppingCart,
      command: Command
  ): ReplyEffect[Event, State] = command match {
    case AddItem(itemId, quantity, replyTo) =>
      Effect.reply(replyTo)(Rejected("Cart is checked out"))
    case RemoveItem(itemId, replyTo) =>
      Effect.reply(replyTo)(Rejected("Cart is checked out"))
    case AdjustItemQuantity(itemId, quantity, replyTo) =>
      Effect.reply(replyTo)(Rejected("Cart is checked out"))
    case Checkout(replyTo) =>
      Effect.reply(replyTo)(Rejected("Cart is checked out"))
    case Get(replyTo) =>
      Effect.reply(replyTo)(cart.toSummary)
  }

}
