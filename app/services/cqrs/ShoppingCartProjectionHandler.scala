package services.cqrs

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.eventstream.EventStream
import akka.projection.eventsourced.EventEnvelope
import akka.projection.slick.SlickHandler
import slick.dbio.DBIO
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class ShoppingCartProjectionHandler(tag: String, system: ActorSystem[_])
    extends SlickHandler[EventEnvelope[ShoppingCart.Event]] {
  val log = LoggerFactory.getLogger(getClass)

  override def process(
      envelope: EventEnvelope[ShoppingCart.Event]
  ): DBIO[Done] = {

    log.info(
      "EventProcessor({}) consumed {} from {} with seqNr {}",
      tag,
      envelope.event,
      envelope.persistenceId,
      envelope.sequenceNr
    )
    system.eventStream ! EventStream.Publish(envelope.event)
    DBIO.successful(Done)
  }
}
