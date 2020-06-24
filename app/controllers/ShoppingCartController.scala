package controllers

import javax.inject.Inject

import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import play.api.libs.json.Json
import services.CounterComponent

import scala.concurrent.duration._
import play.api.mvc.AbstractController
import scala.concurrent.ExecutionContext
import play.api.mvc.ControllerComponents
import scala.concurrent.Future
import akka.actor.typed.Scheduler
import play.api.Logger
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.actor.ActorSystem

import akka.actor.typed.scaladsl.adapter._
import services.cqrs.ShoppingCart
import services.cqrs.ShoppingCart.Accepted
import services.cqrs.ShoppingCart.Rejected
import services.cqrs.ShoppingCart.Summary

class ShoppingCartController @Inject() (
    counterComponent: CounterComponent,
    cc: ControllerComponents,
    system: ActorSystem
)(implicit ex: ExecutionContext, scheduler: Scheduler)
    extends AbstractController(cc) {

  private val sharding = ClusterSharding(system.toTyped)

  def addItem(cartId: String) = Action.async { request =>
    implicit val timeout: Timeout = Timeout(5.seconds)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
    val (itemId, quantity) =
      request.body.asJson
        .map(json =>
          ((json \ "item_id").as[String], (json \ "quantity").as[Int])
        )
        .get
    val reply: Future[ShoppingCart.Confirmation] =
      entityRef.ask(ShoppingCart.AddItem(itemId, quantity, _))

    reply.map({
      case ShoppingCart.Accepted(summary) => Ok(summary.toString())
      case Rejected(reason)               => BadRequest(reason)
    })
  }

  def removeItem(cartId: String) = Action.async { request =>
    implicit val timeout: Timeout = Timeout(5.seconds)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
    val itemId =
      request.body.asJson.map(json => ((json \ "item_id").as[String])).get
    val reply: Future[ShoppingCart.Confirmation] =
      entityRef.ask(ShoppingCart.RemoveItem(itemId, _))

    reply.map({
      case ShoppingCart.Accepted(summary) => Ok(summary.toString())
      case Rejected(reason)               => BadRequest(reason)
    })
  }

  def adjustItemQuantity(cartId: String) = Action.async { request =>
    implicit val timeout: Timeout = Timeout(5.seconds)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
    val (itemId, quantity) =
      request.body.asJson
        .map(json =>
          ((json \ "item_id").as[String], (json \ "quantity").as[Int])
        )
        .get
    val reply: Future[ShoppingCart.Confirmation] =
      entityRef.ask(ShoppingCart.AdjustItemQuantity(itemId, quantity, _))

    reply.map({
      case ShoppingCart.Accepted(summary) => Ok(summary.toString())
      case Rejected(reason)               => BadRequest(reason)
    })
  }

  def checkout(cartId: String) = Action.async { request =>
    implicit val timeout: Timeout = Timeout(5.seconds)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
    val reply: Future[ShoppingCart.Confirmation] =
      entityRef.ask(ShoppingCart.Checkout(_))

    reply.map({
      case ShoppingCart.Accepted(summary) => Ok(summary.toString())
      case Rejected(reason)               => BadRequest(reason)
    })
  }

  def get(cartId: String) = Action.async { request =>
    implicit val timeout: Timeout = Timeout(5.seconds)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
    val reply: Future[ShoppingCart.Summary] =
      entityRef.ask(ShoppingCart.Get(_))

    reply.map({
      case Summary(items, checkedOut) =>
        Ok(items.toString() + " " + (if (checkedOut) "checked out" else "open"))
    })
  }
}
