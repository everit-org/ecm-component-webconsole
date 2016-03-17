package org.everit.osgi.ecm.component.webconsole;

/**
 * Converter for clause attribute values for their String representation.
 */
public interface ClauseAttributeValueConverter {

  String toString(String key, Object value);
}
