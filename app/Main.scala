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
import akka.persistence.jdbc
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import akka.actor.typed.SpawnProtocol
import scala.concurrent.ExecutionContext
import akka.util.Timeout
import services.cqrs.ShoppingCart
import services.cqrs.EventProcessorSettings
import akka.actor.typed.ActorRef
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.projection.slick.SlickProjection
import slick.basic.DatabaseConfig
import services.cqrs.ShoppingCartProjectionHandler
import akka.persistence.query.Offset
import slick.jdbc.JdbcProfile
import scala.reflect.ClassTag
import play.api.db.slick.DatabaseConfigProvider
import com.google.inject.Provides
import play.api.libs.concurrent.ActorModule
import akka.projection.StatusObserver
import akka.projection.HandlerRecoveryStrategy
import akka.projection.Projection
@Singleton final class Main @Inject() (
    val mainActor: ActorRef[Main.NotUsed]
)

object Main extends ActorModule {

  type Message = NotUsed

  def createProjectionFor[P <: JdbcProfile: ClassTag](
      settings: EventProcessorSettings,
      dbConfig: DatabaseConfig[P],
      index: Int
  )(
      implicit system: ActorSystem[_]
  ): Projection[EventEnvelope[ShoppingCart.Event]] = {
    val tag = s"${settings.tagPrefix}-$index"
    val sourceProvider = EventSourcedProvider.eventsByTag[ShoppingCart.Event](
      system = system,
      readJournalPluginId = JdbcReadJournal.Identifier,
      tag = tag
    )
    SlickProjection.exactlyOnce[Offset, EventEnvelope[ShoppingCart.Event], P](
      projectionId = ProjectionId("shopping-carts", tag),
      sourceProvider,
      dbConfig,
      handler = () => new ShoppingCartProjectionHandler(tag, system)
    )
  }

  sealed trait NotUsed

  def apply[P <: JdbcProfile: ClassTag](
      dbConfig: DatabaseConfig[P]
  ): Behavior[NotUsed] = {
    Behaviors.setup[NotUsed] { context =>
      implicit val system = context.system

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

        ShardedDaemonProcess(system).init(
          name = "ShoppingCartProjection",
          settings.parallelism,
          n =>
            ProjectionBehavior(
              createProjectionFor(settings, dbConfig, n)
            ),
          shardedDaemonProcessSettings,
          Some(ProjectionBehavior.Stop)
        )
      }

      Behaviors.empty
    }
  }

  @Provides
  def create(dbConfigProvider: DatabaseConfigProvider): Behavior[NotUsed] = {
    Main(dbConfigProvider.get[JdbcProfile])
  }
}
