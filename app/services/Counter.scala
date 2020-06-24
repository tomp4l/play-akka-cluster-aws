package services

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.cluster.typed.ClusterSingleton
import akka.cluster.typed.SingletonActor
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.ActorRef
import play.api.ApplicationLoader
import play.api.libs.concurrent.AkkaGuiceSupport
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.actor.typed.scaladsl.adapter._

@Singleton
class CounterComponent @Inject() (actorSystem: ActorSystem) {

  val singletonManager = ClusterSingleton(actorSystem.toTyped)

  val counter: ActorRef[Counter.Command] = singletonManager.init(
    SingletonActor(
      Behaviors
        .supervise(Counter())
        .onFailure[Exception](SupervisorStrategy.restart),
      "GlobalCounter"
    )
  )
}

object Counter {
  sealed trait Command
  case class Count(replyTo: ActorRef[Reply]) extends Command
  case class Reply(value: Int)

  def apply(): Behavior[Command] = {
    def updated(value: Int): Behavior[Command] =
      Behaviors.receive[Command] {
        case (context, Count(replyTo)) =>
          context.log.info(s"Recieved message")
          replyTo ! Reply(value)
          updated(value + 1)
      }

    updated(0)
  }
}
