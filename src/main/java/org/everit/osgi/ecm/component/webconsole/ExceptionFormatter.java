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

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Formats and exception to text that can be written to output.
 */
public class ExceptionFormatter {

  /**
   * Converts a throwable to a String by calling {@link Throwable#printStackTrace(PrintWriter)}.
   *
   * @param throwable
   *          The {@link Throwable} that should be converted to a {@link String} representation.
   *
   * @return The {@link String} representation of the {@link Throwable}.
   */
  public String format(final Throwable throwable) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    throwable.printStackTrace(pw);
    return sw.toString();
  }
}
