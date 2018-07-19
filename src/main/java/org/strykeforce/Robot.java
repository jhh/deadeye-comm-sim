package org.strykeforce;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.strykeforce.ConnectionEvent.CONNECTED;
import static org.strykeforce.ConnectionEvent.DISCONNECTED;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingWindowReservoir;
import io.reactivex.Observable;
import io.reactivex.schedulers.Timed;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class Robot {

  private static final String PING = "ping";
  private static final String PONG = "pong";
  private static final int PONG_SZ = PONG.getBytes().length;

  // From vision_processor.h:
  //         struct Data {
  //            jint latency;
  //            jint reserved; // struct 64-bit member alignment
  //            jdouble values[4];
  //        };
  private static final int DATA_SZ = (2 * Integer.BYTES) + (4 * Double.BYTES);

  private static final int PING_INTERVAL = 100;
  private static final int PONG_LIMIT = PING_INTERVAL * 4;
  private static final int PORT = 5555;
  private final InetSocketAddress ADDRESS = new InetSocketAddress("192.168.42.129", PORT);

  private final MetricRegistry metrics = new MetricRegistry();
  private final Meter fps = metrics.meter("FPS");
  private final Histogram latency;

  public Robot() {
    latency = new Histogram(new SlidingWindowReservoir(500));
    metrics.register("Latency (ms)", latency);
  }

  private static void debugDatagramPacket(DatagramPacket p) {
    byte[] b = Arrays.copyOf(p.getData(), p.getLength());
    debugByteArray(b);
  }

  private static void debugByteBuffer(ByteBuffer b) {
    b.rewind();
    byte[] bytes = new byte[b.remaining()];
    b.get(bytes);
    debugByteArray(bytes);
  }

  private static void debugByteArray(byte[] b) {
    System.out.println("Bytes = " + Arrays.toString(b));
  }

  public void start() {
    System.out.printf(
        "Starting pings to %s:%d at %d ms interval.%n", ADDRESS.getHostName(), PORT, PING_INTERVAL);

    // send pings
    Observable.interval(PING_INTERVAL, MILLISECONDS)
        .map(i -> PING)
        .subscribe(RxUdp.observerTo(ADDRESS));

    // monitor pongs
    System.out.printf("Listening for pongs on port %d with limit %d ms.%n", PORT, PONG_LIMIT);

    Observable<DatagramPacket> messages = RxUdp.observableFrom(PORT).publish().autoConnect();

    Observable<Timed<String>> pongs =
        messages
            .filter(p -> p.getLength() == PONG_SZ)
            .map(p -> new String(p.getData(), 0, p.getLength()))
            .timestamp(MILLISECONDS);

    Observable<Timed<Long>> heartbeat =
        Observable.interval(PING_INTERVAL / 2, MILLISECONDS).timestamp(MILLISECONDS);

    Observable.combineLatest(pongs, heartbeat, (p, h) -> h.time() - p.time())
        .distinctUntilChanged(time -> time > PONG_LIMIT)
        .map(time -> time > PONG_LIMIT ? DISCONNECTED : CONNECTED)
        .startWith(DISCONNECTED)
        .subscribe(System.out::println);

    ByteBuffer byteBuffer = ByteBuffer.allocate(DATA_SZ);
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

    messages
        .filter(p -> p.getLength() == DATA_SZ)
        .map(DatagramPacket::getData)
        .map(
            bytes -> {
              byteBuffer.clear();
              byteBuffer.put(bytes, 0, DATA_SZ);
              return byteBuffer;
            })
        .map(VisionData::new)
        .subscribe(
            vd -> {
              latency.update(vd.latency);
              fps.mark();
            },
            Throwable::printStackTrace);

    ConsoleReporter reporter =
        ConsoleReporter.forRegistry(metrics)
            .outputTo(System.err)
            .convertRatesTo(SECONDS)
            .convertDurationsTo(MILLISECONDS)
            .build();
    reporter.start(10, SECONDS);
  }
}
