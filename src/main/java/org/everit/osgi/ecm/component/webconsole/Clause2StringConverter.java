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

import java.lang.reflect.Array;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;

/**
 * Util class to show the template.
 */
public class Clause2StringConverter {

  public static final String CLAUSE_SEPARATOR = ";";

  /**
   * Converts a clause Map (Attributes or Directives) to its {@link String} representation.
   *
   * @param clauseMap
   *          Attributes or directives of the clause.
   * @param equalExpr
   *          In case of directives this should be ':=', while in case of attributes it should be a
   *          simple '='.
   * @return The {@link String} representation of clauseMap.
   */
  public String convertClauseMapToString(final Map<String, Object> clauseMap,
      final String equalExpr) {
    StringBuilder sb = new StringBuilder();
    Set<Entry<String, Object>> clauseEntrySet = clauseMap.entrySet();
    boolean first = true;
    for (Entry<String, Object> clauseEntry : clauseEntrySet) {
      if (!first) {
        sb.append(CLAUSE_SEPARATOR);
      }

      sb.append(clauseEntry.getKey()).append(equalExpr)
          .append(escape(convertEntryValueToString(clauseEntry.getValue())));

      first = false;

    }
    return sb.toString();
  }

  /**
   * Converts a clause to a string.
   *
   * @param namespace
   *          The namespace of the clause.
   * @param attributes
   *          The attributes of the clause.
   * @param directives
   *          The directives of the clause.
   * @return The string representation of the clause.
   */
  public String convertClauseToString(final String namespace,
      final Map<String, Object> attributes,
      final Map<String, String> directives) {
    StringBuilder sb = new StringBuilder(namespace);

    if (attributes.size() > 0) {
      sb.append(CLAUSE_SEPARATOR);
    }
    sb.append(convertClauseMapToString(attributes, "="));

    if (directives.size() > 0) {
      sb.append(CLAUSE_SEPARATOR);
    }
    @SuppressWarnings({ "unchecked", "rawtypes" })
    Map<String, Object> castedDirectives = (Map) directives;
    sb.append(convertClauseMapToString(castedDirectives, ":="));

    return sb.toString();
  }

  /**
   * Formats and object to a String as it is shown in requirements and capabilities. The object is
   * converted in a special way if it is an array, a {@link Requirement} or a {@link Capability}.
   *
   * @param object
   *          The {@link Object} that is translated to a human readable {@link String}.
   * @return The {@link String} representation of object.
   */
  public String convertEntryValueToString(final Object object) {
    if (object == null) {
      return "";
    }
    Class<? extends Object> objectType = object.getClass();
    if (objectType.isArray()) {
      StringBuilder sb = new StringBuilder("[");
      int length = Array.getLength(object);
      for (int i = 0; i < length; i++) {
        if (i > 0) {
          sb.append(", ");
        }
        Object entry = Array.get(object, i);
        if (entry != null) {
          sb.append(convertEntryValueToString(entry));
        }
      }
      sb.append("]");
      return sb.toString();
    }
    if (object instanceof Capability) {
      Capability capability = (Capability) object;
      return convertClauseToString(capability.getNamespace(), capability.getAttributes(),
          capability.getDirectives());
    }
    if (object instanceof Requirement) {
      Requirement requirement = (Requirement) object;
      return convertClauseToString(requirement.getNamespace(), requirement.getAttributes(),
          requirement.getDirectives());
    }
    return String.valueOf(object);
  }

  public String escape(final String text) {
    if (text == null) {
      return "";
    }
    return text.replace(CLAUSE_SEPARATOR, "\\;").replace("\"", "\\\"").replace("\\", "\\\\");
  }
}
