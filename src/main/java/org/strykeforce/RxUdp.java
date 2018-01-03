package org.strykeforce;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;

public class RxUdp {

  private final DatagramSocket socket;

  private RxUdp() {
    try {
      socket = new DatagramSocket();
    } catch (SocketException e) {
      throw new RuntimeException(e);
    }
  }

  public static Observer<String> observerTo(SocketAddress address) {
    return new UdpObserver(address);
  }

  public static Observable<DatagramPacket> observableFrom(int port) {
    return Observable.<DatagramPacket>create(
            e -> {
              DatagramSocket socket = new DatagramSocket(port);
              e.setCancellable(socket::close);
              byte[] buf = new byte[1024];
              DatagramPacket packet = new DatagramPacket(buf, buf.length);
              for (int i = 0; ; i++) {
                try {
                  socket.receive(packet);
                } catch (IOException ioe) {
                  if (socket.isClosed()) {
                    e.onComplete();
                    break;
                  } else {
                    e.onError(ioe);
                    break;
                  }
                }
                e.onNext(packet);
              }
            })
        .subscribeOn(Schedulers.io());
  }

  private static class UdpObserver implements Observer<String> {
    private final SocketAddress address;
    private DatagramSocket socket;
    private Disposable sub;

    public UdpObserver(SocketAddress address) {
      this.address = address;
    }

    @Override
    public void onSubscribe(Disposable d) {
      sub = d;
      try {
        socket = new DatagramSocket();
      } catch (SocketException e) {
        e.printStackTrace();
        sub.dispose();
      }
    }

    @Override
    public void onNext(String s) {
      byte[] buf = s.getBytes();
      DatagramPacket packet = new DatagramPacket(buf, buf.length, address);
      try {
        socket.send(packet);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    @Override
    public void onError(Throwable e) {
      e.printStackTrace();
    }

    @Override
    public void onComplete() {}
  }
}
