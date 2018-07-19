package org.strykeforce;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class VisionData {

  final int latency;
  final double[] data = new double[4];

  public VisionData(ByteBuffer bytes) {
    bytes.rewind();
    latency = bytes.getInt();
    bytes.getInt(); // skip 4 bytes for 64-bit alignment in Data struct
    for (int i = 0; i < 4; i++) {
      data[i] = bytes.getDouble();
    }
  }

  @Override
  public String toString() {
    return "VisionData{" + "latency=" + latency + ", data=" + Arrays.toString(data) + '}';
  }
}
