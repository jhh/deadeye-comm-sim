package org.strykeforce;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class VisionData {

  public static final int TYPE_FRAME_DATA = 0xDEADDA7A;
  public static final int TYPE_PING = 0xDEADBACC;
  public static final int TYPE_PONG = 0xDEADCCAB;

  final int type;
  final double[] data = new double[4];
  int latency;

  VisionData(byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    buffer.order(Robot.BYTE_ORDER);
    buffer.rewind();
    type = buffer.getInt();

    switch (type) {
      case TYPE_FRAME_DATA:
        latency = buffer.getInt();
        for (int i = 0; i < 4; i++) {
          data[i] = buffer.getDouble();
        }
//        System.out.println("FRAME DATA:" + Arrays.toString(data));
        break;

      case TYPE_PONG:
        //        System.out.println("PONG DATA");
        break;

      default:
        System.out.println("UNKNOWN TYPE: " + type);
    }
  }

  @Override
  public String toString() {
    return "VisionData{"
        + "type="
        + String.format("0x%08X", type)
        + ", data="
        + Arrays.toString(data)
        + ", latency="
        + latency
        + '}';
  }
}
