package shopping.cart

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.SendProducer
import akka.persistence.query.Offset
import akka.persistence.r2dbc.query.javadsl.R2dbcReadJournal
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.r2dbc.scaladsl.R2dbcProjection
import akka.projection.scaladsl.{AtLeastOnceProjection, SourceProvider}
import akka.projection.{ProjectionBehavior, ProjectionId}
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

object PublishKafkaEventsProjection {

  def init(system: ActorSystem[_]): Unit = {
    val sendProducer = createProducer(system)
    val topic =
      system.settings.config.getString("shopping-cart-service.kafka.topic")

    ShardedDaemonProcess(system).init(
      name = "PublishEventsProjection",
      ShoppingCart.tags.size,
      index =>
        ProjectionBehavior(
          createProjectionFor(system, topic, sendProducer, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop))
  }

  private def createProducer(
      system: ActorSystem[_]): SendProducer[String, Array[Byte]] = {
    val producerSettings =
      ProducerSettings(system, new StringSerializer, new ByteArraySerializer)
    val sendProducer =
      SendProducer(producerSettings)(system)
    CoordinatedShutdown(system).addTask(
      CoordinatedShutdown.PhaseBeforeActorSystemTerminate,
      "close-sendProducer") { () =>
      sendProducer.close()
    }
    sendProducer
  }

  private def createProjectionFor(
      system: ActorSystem[_],
      topic: String,
      sendProducer: SendProducer[String, Array[Byte]],
      index: Int)
      : AtLeastOnceProjection[Offset, EventEnvelope[ShoppingCart.Event]] = {
    val tag = ShoppingCart.tags(index)
    val sourceProvider
        : SourceProvider[Offset, EventEnvelope[ShoppingCart.Event]] =
      EventSourcedProvider.eventsByTag[ShoppingCart.Event](
        system = system,
        readJournalPluginId = R2dbcReadJournal.Identifier,
        tag = tag)

    R2dbcProjection.atLeastOnceAsync(
      projectionId = ProjectionId("PublishEventsProjection", tag),
      None,
      sourceProvider,
      handler =
        () => new PublishKafkaEventsProjectionHandler(system, topic, sendProducer))(system)
  }

}
