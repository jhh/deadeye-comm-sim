package org.strykeforce;

import com.codahale.metrics.*;
import io.reactivex.Observable;
import io.reactivex.schedulers.Timed;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.strykeforce.ConnectionEvent.CONNECTED;
import static org.strykeforce.ConnectionEvent.DISCONNECTED;

public class Robot {

  public static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

  private static final byte[] PING_BYTES =
      ByteBuffer.allocate(4).order(BYTE_ORDER).putInt(VisionData.TYPE_PING).array();

  //  From FrameProcessor.h:
  //  struct Data {
  //    jint type;
  //    jint latency;
  //    jdouble values[4];
  //  };

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
        .map(i -> PING_BYTES)
        .subscribe(RxUdp.observerTo(ADDRESS));

    // monitor pongs
    System.out.printf("Listening for pongs on port %d with limit %d ms.%n", PORT, PONG_LIMIT);

    Observable<DatagramPacket> packetObservable =
        RxUdp.observableFrom(PORT).publish().autoConnect();

    Observable<VisionData> visionDataObservable =
        packetObservable.map(DatagramPacket::getData).map(VisionData::new);

    Observable<Timed<VisionData>> pongs =
        visionDataObservable.filter(vd -> vd.type == VisionData.TYPE_PONG).timestamp(MILLISECONDS);

    Observable<Timed<Long>> heartbeat =
        Observable.interval(PING_INTERVAL / 2, MILLISECONDS).timestamp(MILLISECONDS);

    Observable.combineLatest(pongs, heartbeat, (p, h) -> h.time() - p.time())
        .distinctUntilChanged(time -> time > PONG_LIMIT)
        .map(time -> time > PONG_LIMIT ? DISCONNECTED : CONNECTED)
        .startWith(DISCONNECTED)
        .subscribe(System.out::println, Throwable::printStackTrace);

    visionDataObservable
        //        .filter(vd -> vd.type != VisionData.TYPE_PONG)
        .doOnNext(System.out::println)
        .filter(vd -> vd.type == VisionData.TYPE_FRAME_DATA)
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
