package org.strykeforce;

import com.codahale.metrics.*;
import org.strykeforce.thirdcoast.deadeye.DeadeyeMessage;
import org.strykeforce.thirdcoast.deadeye.DeadeyeService;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Main {

  public static void main(String[] args) {

    MetricRegistry metrics = new MetricRegistry();
    Histogram latency = new Histogram(new SlidingWindowReservoir(500));
    metrics.register("Latency (ms)", latency);
    Meter fps = metrics.meter("FPS");

    DeadeyeService deadeyeService = new DeadeyeService();
    deadeyeService.enableConnectionEventLogging(false);

    deadeyeService
        .getConnectionEventObservable()
        .subscribe(System.out::println, Throwable::printStackTrace);

    deadeyeService
        .getMessageObservable()
        .filter(deadeyeMessage -> deadeyeMessage.type == DeadeyeMessage.Type.DATA)
        .subscribe(
            deadeyeMessage -> {
              latency.update(deadeyeMessage.latency);
              fps.mark();
//              System.out.printf(
//                  "x = %f, y = %f, h = %f, w = %f%n",
//                  deadeyeMessage.data[0],
//                  deadeyeMessage.data[1],
//                  deadeyeMessage.data[2],
//                  deadeyeMessage.data[3]);
            },
            Throwable::printStackTrace);

    ConsoleReporter reporter =
        ConsoleReporter.forRegistry(metrics)
            .outputTo(System.out)
            .convertRatesTo(SECONDS)
            .convertDurationsTo(MILLISECONDS)
            .build();
    reporter.start(30, SECONDS);

    sleep(Long.MAX_VALUE);
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
