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

class CounterController @Inject() (
    counterComponent: CounterComponent,
    cc: ControllerComponents
)(implicit ex: ExecutionContext, scheduler: Scheduler)
    extends AbstractController(cc) {
  import services.Counter._

  val logger = Logger(getClass)
  val counter = counterComponent.counter

  def count() = Action.async {
    logger.info("Recieved request")
    implicit val timeout = Timeout(5.seconds)
    val result: Future[Reply] = counter.ask(ref => Count(ref))
    result.map(reply => Ok(Json.toJson(reply.value)))
  }

}
