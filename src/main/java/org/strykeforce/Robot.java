package org.strykeforce;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.reactivex.Observable;
import io.reactivex.schedulers.Timed;
import java.net.InetSocketAddress;

public class Robot {

  private static final String PING = "ping";
  private static final String PONG = "pong";
  private static final int PING_INTERVAL = 1000;
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

    Observable<String> messages = RxUdp.observableFrom(PORT).publish().autoConnect();

    Observable<Timed<String>> pongs = messages.filter(s -> s.equals(PONG)).timestamp(MILLISECONDS);

    Observable<Timed<Long>> heartbeat =
        Observable.interval(PING_INTERVAL / 2, MILLISECONDS).timestamp(MILLISECONDS);

    Observable.combineLatest(pongs, heartbeat, (p, h) -> h.time() - p.time())
        .distinctUntilChanged(time -> time > PONG_LIMIT)
        .map(time -> time > PONG_LIMIT ? ConnectionEvent.DISCONNECTED : ConnectionEvent.CONNECTED)
        .subscribe(System.out::println);
  }
}
