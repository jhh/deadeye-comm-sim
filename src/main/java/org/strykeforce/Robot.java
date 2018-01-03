package org.strykeforce;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.strykeforce.ConnectionEvent.CONNECTED;
import static org.strykeforce.ConnectionEvent.DISCONNECTED;

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
  private static final int PING_INTERVAL = 100;
  private static final int PONG_LIMIT = PING_INTERVAL * 4;
  private static final int PORT = 5555;
  private final InetSocketAddress ADDRESS = new InetSocketAddress("192.168.42.129", PORT);

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
            .map(p -> new String(p.getData(), 0, p.getLength()))
            .filter(s -> s.equals(PONG))
            .timestamp(MILLISECONDS);

    Observable<Timed<Long>> heartbeat =
        Observable.interval(PING_INTERVAL / 2, MILLISECONDS).timestamp(MILLISECONDS);

    Observable.combineLatest(pongs, heartbeat, (p, h) -> h.time() - p.time())
        .distinctUntilChanged(time -> time > PONG_LIMIT)
        .map(time -> time > PONG_LIMIT ? DISCONNECTED : CONNECTED)
        .startWith(DISCONNECTED)
        .subscribe(System.out::println);

    ByteBuffer byteBuffer = ByteBuffer.allocate(32);
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

    messages
        .filter(p -> p.getLength() == 32)
//        .doOnNext(Robot::debugDatagramPacket)
        .map(DatagramPacket::getData)
//        .doOnNext(Robot::debugByteArray)
        .map(
            bytes -> {
              byteBuffer.clear();
              byteBuffer.put(bytes, 0, 32);
              byteBuffer.rewind();
              return byteBuffer;
            })
//        .doOnNext(Robot::debugByteBuffer)
        .map(ByteBuffer::asDoubleBuffer)
        .doOnNext(db -> System.out.printf("DoubleBuffer capacity = %d%n", db.capacity()))
        .map(
            db -> {
              double[] dest = new double[db.capacity()];
              db.get(dest);
              return dest;
            })
        .map(Arrays::toString)
        .subscribe(System.out::println, throwable -> throwable.printStackTrace());
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
}
