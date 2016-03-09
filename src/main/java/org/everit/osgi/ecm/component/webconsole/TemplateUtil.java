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

import org.everit.osgi.ecm.component.ECMComponentConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;

/**
 * Util class to show the template.
 */
public class TemplateUtil {

  private static final String CLAUSE_SEPARATOR = ";";

  private void appendStateToSBIfMatches(final StringBuilder sb, final int stateMask,
      final int expectedState,
      final String stateName) {
    if ((stateMask & expectedState) > 0) {
      if (sb.length() > 0) {
        sb.append("|");
      }
      sb.append(stateName);
    }
  }

  private String convertClauseToString(final String namespace,
      final Map<String, String> directives,
      final Map<String, Object> attributes) {
    StringBuilder sb = new StringBuilder(namespace);

    if (directives.size() > 0) {
      sb.append(CLAUSE_SEPARATOR);
    }
    @SuppressWarnings({ "unchecked", "rawtypes" })
    Map<String, Object> castedDirectives = (Map) directives;
    sb.append(translateClauseMap(castedDirectives, ":="));

    if (attributes.size() > 0) {
      sb.append(CLAUSE_SEPARATOR);
    }
    sb.append(translateClauseMap(attributes, "="));
    return sb.toString();
  }

  private String escapeClauseValue(final String text) {
    if (text == null) {
      return "";
    }
    return text.replace(CLAUSE_SEPARATOR, "\\;").replace("\"", "\\\"").replace("\\", "\\\\");
  }

  /**
   * Formats and object to a String as it is shown in requirements and capabilities. The object is
   * converted in a special way if it is an array, a {@link Requirement} or a {@link Capability}.
   *
   * @param object
   *          The {@link Object} that is translated to a human readable {@link String}.
   * @return The {@link String} representation of object.
   */
  public String toString(final Object object) {
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
          sb.append(toString(entry));
        }
      }
      sb.append("]");
      return sb.toString();
    }
    if (object instanceof Capability) {
      Capability capability = (Capability) object;
      return convertClauseToString(capability.getNamespace(), capability.getDirectives(),
          capability.getAttributes());
    }
    if (object instanceof Requirement) {
      Requirement requirement = (Requirement) object;
      return convertClauseToString(requirement.getNamespace(), requirement.getDirectives(),
          requirement.getAttributes());
    }
    return String.valueOf(object);
  }

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
  public String translateClauseMap(final Map<String, Object> clauseMap, final String equalExpr) {
    StringBuilder sb = new StringBuilder();
    Set<Entry<String, Object>> clauseEntrySet = clauseMap.entrySet();
    boolean first = true;
    boolean isEcmFactory = clauseMap
        .containsKey(ECMComponentConstants.SERVICE_PROP_COMPONENT_SERVICE_PID);
    for (Entry<String, Object> clauseEntry : clauseEntrySet) {
      if (!first) {
        sb.append(CLAUSE_SEPARATOR);
      }
      if (clauseEntry.getKey().equals(Constants.SERVICE_ID)) {
        sb.append("<a href=\"services/" + clauseEntry.getValue()
            + "\" class=\"reqCapLink\">service.id=" + clauseEntry.getValue() + "</a>");
      } else if (clauseEntry.getKey()
          .equals(ECMComponentConstants.SERVICE_PROP_COMPONENT_SERVICE_PID)) {
        sb.append("<a href=\"#" + clauseEntry.getValue()
            + "\" class=\"reqCapLink\" onclick=\"componentLinkClick(event, '"
            + clauseEntry.getValue() + "');return false;\">"
            + ECMComponentConstants.SERVICE_PROP_COMPONENT_SERVICE_PID + "="
            + clauseEntry.getValue() + "</a>");
      } else if (!isEcmFactory
          && clauseEntry.getKey().equals(ECMComponentConstants.SERVICE_PROP_COMPONENT_ID)) {
        sb.append("<a href=\"#" + clauseEntry.getValue()
            + "\" class=\"reqCapLink\" onclick=\"componentLinkClick(event, '"
            + clauseEntry.getValue() + "');return false;\">"
            + ECMComponentConstants.SERVICE_PROP_COMPONENT_ID + "="
            + clauseEntry.getValue() + "</a>");
      } else {
        sb.append(clauseEntry.getKey()).append(equalExpr)
            .append(escapeClauseValue(toString(clauseEntry.getValue())));
      }
      first = false;

    }
    return sb.toString();
  }

  /**
   * Translates a Bundle stateMask to a human readable format. Only {@link Bundle#RESOLVED},
   * {@link Bundle#STARTING}, {@link Bundle#ACTIVE} and {@link Bundle#STOPPING} states are
   * translated.
   *
   * @param stateMask
   *          The stateMask of the Bundle.
   * @return The {@link String} representation of the stateMask. E.g.: 'RESOLVED, STARTING'.
   */
  public String translateStateMask(final int stateMask) {
    StringBuilder sb = new StringBuilder();
    appendStateToSBIfMatches(sb, stateMask, Bundle.RESOLVED, "RESOLVED");
    appendStateToSBIfMatches(sb, stateMask, Bundle.STARTING, "STARTING");
    appendStateToSBIfMatches(sb, stateMask, Bundle.ACTIVE, "ACTIVE");
    appendStateToSBIfMatches(sb, stateMask, Bundle.STOPPING, "STOPPING");
    return sb.toString();
  }
}
