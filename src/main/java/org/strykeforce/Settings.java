package org.strykeforce;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public interface Settings {

  int ROBOT_PORT = 5555;
  int VISION_PORT = 5555;
  SocketAddress VISION_ADDRESS = new InetSocketAddress("192.168.42.129", VISION_PORT);
  SocketAddress ROBOT_ADDRESS = new InetSocketAddress("192.168.42.190", ROBOT_PORT);
//  SocketAddress VISION_ADDRESS = new InetSocketAddress("127.0.0.1", VISION_PORT);
//  SocketAddress ROBOT_ADDRESS = new InetSocketAddress("127.0.0.1", ROBOT_PORT);

  int PING_INTERVAL = 100;
  int PONG_LIMIT = 400;

}
