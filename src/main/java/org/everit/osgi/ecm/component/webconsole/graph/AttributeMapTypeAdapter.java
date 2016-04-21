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
package org.everit.osgi.ecm.component.webconsole.graph;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * A type adapter that helps serializing the attribute maps to JSON. Arrays remain arrays, but
 * toString is used on all other objects.
 */
public class AttributeMapTypeAdapter extends TypeAdapter<AttributeMap> {

  @Override
  public AttributeMap read(final JsonReader in) throws IOException {
    // Do nothing as we do not use this adapter to read values.
    return null;
  }

  @Override
  public void write(final JsonWriter out, final AttributeMap attributeMap) throws IOException {
    out.beginObject();
    Set<Entry<String, Object>> entrySet = attributeMap.entrySet();
    for (Entry<String, Object> entry : entrySet) {
      out.name(entry.getKey());

      Object value = entry.getValue();
      writeObjectValueToJsonWriter(out, value);
    }
    out.endObject();
  }

  private void writeObjectValueToJsonWriter(final JsonWriter out, final Object value)
      throws IOException {

    if (value == null) {
      out.nullValue();
      return;
    }

    Class<? extends Object> valueType = value.getClass();
    if (valueType.isArray()) {
      out.beginArray();
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        out.value(String.valueOf(Array.get(value, i)));
      }
      out.endArray();
    } else {
      out.value(String.valueOf(value));
    }
  }

}
