import java.io.File
import java.util.concurrent.CountDownLatch
import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.{
  ClusterShardingSettings,
  ShardedDaemonProcessSettings
}
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.cluster.typed.Cluster
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import akka.actor.typed.SpawnProtocol
import scala.concurrent.ExecutionContext
import akka.util.Timeout
import sevices.cqrs.ShoppingCart
import sevices.cqrs.EventProcessorSettings
import akka.actor.typed.ActorRef
@Singleton final class Main @Inject() (
    val mainActor: ActorRef[Main.NotUsed]
)

object Main {

  // def createProjectionFor(
  //     system: ActorSystem[_],
  //     settings: EventProcessorSettings,
  //     index: Int
  // ): AtLeastOnceCassandraProjection[EventEnvelope[ShoppingCart.Event]] = {
  //   val tag = s"${settings.tagPrefix}-$index"
  //   val sourceProvider = EventSourcedProvider.eventsByTag[ShoppingCart.Event](
  //     system = system,
  //     readJournalPluginId = CassandraReadJournal.Identifier,
  //     tag = tag
  //   )
  //   CassandraProjection.atLeastOnce(
  //     projectionId = ProjectionId("shopping-carts", tag),
  //     sourceProvider,
  //     handler = new ShoppingCartProjectionHandler(tag, system)
  //   )
  // }

  sealed trait NotUsed

  def apply(): Behavior[NotUsed] = {
    Behaviors.setup[NotUsed] { context =>
      val system = context.system

      val settings = EventProcessorSettings(system)

      context.log.info("Starting shopping cart system")
      ShoppingCart.init(system, settings)

      if (Cluster(system).selfMember.hasRole("read-model")) {

        // we only want to run the daemon processes on the read-model nodes
        val shardingSettings = ClusterShardingSettings(system)
        val shardedDaemonProcessSettings =
          ShardedDaemonProcessSettings(system).withShardingSettings(
            shardingSettings.withRole("read-model")
          )

        // ShardedDaemonProcess(system).init(
        //   name = "ShoppingCartProjection",
        //   settings.parallelism,
        //   n => ProjectionBehavior(createProjectionFor(system, settings, n)),
        //   shardedDaemonProcessSettings,
        //   Some(ProjectionBehavior.Stop)
        // )
      }

      Behaviors.empty
    }
  }
}
