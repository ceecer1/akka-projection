package shopping.analytics

//#initProjections
import scala.concurrent.Future

import akka.Done
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.Persistence
import akka.persistence.query.typed.EventEnvelope
import akka.projection.ProjectionBehavior
import akka.projection.ProjectionId
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.grpc.consumer.scaladsl.GrpcReadJournal
import akka.projection.r2dbc.scaladsl.R2dbcProjection
import akka.projection.scaladsl.Handler
import org.slf4j.LoggerFactory
import shoppingcart.CheckedOut
import shoppingcart.ItemAdded
import shoppingcart.ItemQuantityAdjusted
import shoppingcart.ItemRemoved
import shoppingcart.ShoppingCartEventsProto

object ShoppingCartEventConsumer {
  //#initProjections

  private val log =
    LoggerFactory.getLogger("shopping.analytics.ShoppingCartEventConsumer")

  //#eventHandler
  private class EventHandler(projectionId: ProjectionId)
      extends Handler[EventEnvelope[AnyRef]] {
    private var totalCount = 0
    private var throughputStartTime = System.nanoTime()
    private var throughputCount = 0

    private var lagCount = 0L
    // JVM System property
    private val lagThresholdMillis =
      Integer.getInteger("ShoppingCartEventConsumer.lag-threshold-ms", 2000)

    override def start(): Future[Done] = {
      log.info("Started Projection [{}].", projectionId.id)
      super.start()
    }
    override def stop(): Future[Done] = {
      log.info(
        "Stopped Projection [{}]. Consumed [{}] events.",
        projectionId.id,
        totalCount)
      super.stop()
    }

    override def process(envelope: EventEnvelope[AnyRef]): Future[Done] = {
      val event = envelope.event
      totalCount += 1

      event match {
        case itemAdded: ItemAdded =>
          log.debug(
            "Projection [{}] consumed ItemAdded for cart {}, added {} {}. Total [{}] events.",
            projectionId.id,
            itemAdded.cartId,
            itemAdded.quantity,
            itemAdded.itemId,
            totalCount)
        case quantityAdjusted: ItemQuantityAdjusted =>
          log.debug(
            "Projection [{}] consumed ItemQuantityAdjusted for cart {}, changed {} {}. Total [{}] events.",
            projectionId.id,
            quantityAdjusted.cartId,
            quantityAdjusted.quantity,
            quantityAdjusted.itemId,
            totalCount)
        case itemRemoved: ItemRemoved =>
          log.debug(
            "Projection [{}] consumed ItemRemoved for cart {}, removed {}. Total [{}] events.",
            projectionId.id,
            itemRemoved.cartId,
            itemRemoved.itemId,
            totalCount)
        case checkedOut: CheckedOut =>
          log.debug(
            "Projection [{}] consumed CheckedOut for cart {}. Total [{}] events.",
            projectionId.id,
            checkedOut.cartId,
            totalCount)
        case unknown =>
          throw new IllegalArgumentException(s"Unknown event $unknown")
      }

      throughputCount += 1
      val durationMs: Long =
        (System.nanoTime - throughputStartTime) / 1000 / 1000
      if (throughputCount >= 1000 || durationMs >= 10000) {
        log.info(
          "Projection [{}] throughput [{}] events/s in [{}] ms. Total [{}] events.",
          projectionId.id,
          1000L * throughputCount / durationMs,
          durationMs,
          totalCount)
        throughputCount = 0
        throughputStartTime = System.nanoTime
      }

      val lagMillis = System.currentTimeMillis() - envelope.timestamp
      if (lagMillis > lagThresholdMillis) {
        lagCount += 1
        if ((lagCount == 1) || (lagCount % 1000 == 0))
          log.info(
            "Projection [{}] lag [{}] ms. Total [{}] events.",
            projectionId.id,
            lagMillis,
            totalCount)
      } else {
        lagCount = 0
      }

      Future.successful(Done)
    }
  }
  //#eventHandler

  //#initProjections
  def init(system: ActorSystem[_]): Unit = {
    implicit val sys: ActorSystem[_] = system
    val numberOfProjectionInstances = 4
    val projectionName: String = "cart-events"
    val sliceRanges =
      Persistence(system).sliceRanges(numberOfProjectionInstances)

    val eventsBySlicesQuery =
      GrpcReadJournal(List(ShoppingCartEventsProto.javaDescriptor))

    ShardedDaemonProcess(system).init(
      projectionName,
      numberOfProjectionInstances,
      { idx =>
        val sliceRange = sliceRanges(idx)
        val projectionKey =
          s"${eventsBySlicesQuery.streamId}-${sliceRange.start}-${sliceRange.end}"
        val projectionId = ProjectionId.of(projectionName, projectionKey)

        val sourceProvider = EventSourcedProvider.eventsBySlices[AnyRef](
          system,
          eventsBySlicesQuery,
          eventsBySlicesQuery.streamId,
          sliceRange.start,
          sliceRange.end)

        ProjectionBehavior(
          R2dbcProjection.atLeastOnceAsync(
            projectionId,
            None,
            sourceProvider,
            () => new EventHandler(projectionId)))
      })
  }

}
//#initProjections
