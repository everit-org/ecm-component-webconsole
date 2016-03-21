/*
 * Copyright (C) 2011 Everit Kft. (http://www.everit.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
