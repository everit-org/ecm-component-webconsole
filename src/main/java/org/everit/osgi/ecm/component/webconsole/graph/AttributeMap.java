package org.everit.osgi.ecm.component.webconsole.graph;

import java.util.Map;
import java.util.TreeMap;

/**
 * A simple type that holds attributes of a capability or properties of a component.
 */
public class AttributeMap extends TreeMap<String, Object> {

  private static final long serialVersionUID = 1L;

  public AttributeMap(final Map<String, ? extends Object> m) {
    super(m);
  }

}
