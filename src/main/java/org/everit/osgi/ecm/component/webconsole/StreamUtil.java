package org.everit.osgi.ecm.component.webconsole;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Util methods for handling contents coming from streams.
 */
public final class StreamUtil {

  /**
   * Reads the content of a stream into a String encoding with UTF8.
   *
   * @param inputStream
   *          The stream to read the data from.
   * @return The content of the stream.
   */
  public static String readContent(final InputStream inputStream) {
    final int bufferSize = 1024;

    byte[] buf = new byte[bufferSize];
    try {
      int r = inputStream.read(buf);
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      while (r > -1) {
        bout.write(buf, 0, r);
        r = inputStream.read(buf);
      }
      return new String(bout.toByteArray(), Charset.forName("UTF8"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private StreamUtil() {
  }
}
