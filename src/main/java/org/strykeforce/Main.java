package org.strykeforce;

import io.reactivex.Flowable;

public class Main {

  public static final RxBus bus = new RxBus();

  public static void main(String[] args) {

    Flowable<Object> connEventEmitter = bus.asFlowable();

    connEventEmitter.ofType(ConnectionEvent.class).subscribe(System.out::println);

    Robot robot = new Robot();
    robot.start();

    sleep(Long.MAX_VALUE);
    //    sleep(4000);
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
