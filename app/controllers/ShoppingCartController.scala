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
import akka.pattern.AskTimeoutException
import play.api.mvc.Result

class ShoppingCartController @Inject() (
    counterComponent: CounterComponent,
    cc: ControllerComponents,
    sharding: ClusterSharding
)(implicit ex: ExecutionContext, scheduler: Scheduler)
    extends AbstractController(cc) {
  implicit val timeout: Timeout = Timeout(10.seconds)

  def addItem(cartId: String) = Action.async { request =>
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
    val (itemId, quantity) =
      request.body.asJson
        .map(json =>
          ((json \ "item_id").as[String], (json \ "quantity").as[Int])
        )
        .get
    val reply =
      entityRef.ask(ShoppingCart.AddItem(itemId, quantity, _))

    reply.mapToResponse
  }

  def removeItem(cartId: String) = Action.async { request =>
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
    val itemId =
      request.body.asJson.map(json => ((json \ "item_id").as[String])).get
    val reply =
      entityRef.ask(ShoppingCart.RemoveItem(itemId, _))

    reply.mapToResponse
  }

  def adjustItemQuantity(cartId: String) = Action.async { request =>
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
    val (itemId, quantity) =
      request.body.asJson
        .map(json =>
          ((json \ "item_id").as[String], (json \ "quantity").as[Int])
        )
        .get
    val reply: Future[ShoppingCart.Confirmation] =
      entityRef.ask(ShoppingCart.AdjustItemQuantity(itemId, quantity, _))

    reply.mapToResponse
  }

  def checkout(cartId: String) = Action.async { request =>
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
    val reply =
      entityRef.ask(ShoppingCart.Checkout(_))

    reply.mapToResponse
  }

  def get(cartId: String) = Action.async { request =>
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
    val reply: Future[ShoppingCart.Summary] =
      entityRef.ask(ShoppingCart.Get(_))

    reply.map({
      case Summary(items, checkedOut) =>
        Ok(items.toString() + " " + (if (checkedOut) "checked out" else "open"))
    })
  }

  implicit class MapToResponse(reply: Future[ShoppingCart.Confirmation]) {
    def mapToResponse: Future[Result] =
      reply
        .map({
          case ShoppingCart.Accepted(summary) => Ok(summary.toString())
          case Rejected(reason)               => BadRequest(reason)
        })
        .recover({
          case e: AskTimeoutException =>
            InternalServerError("Request timed out")
        })
  }
}
