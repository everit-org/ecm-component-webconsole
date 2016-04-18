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

import org.everit.osgi.ecm.component.ECMComponentConstants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

public class ComponentNodeIdBaseData {

  public static ComponentNodeIdBaseData createByServiceRef(
      final ServiceReference<?> serviceReference) {

    String tmpComponentId =
        (String) serviceReference.getProperty(ECMComponentConstants.SERVICE_PROP_COMPONENT_ID);

    if (tmpComponentId == null) {
      return null;
    }

    Version tmpVersion = (Version) serviceReference
        .getProperty(ECMComponentConstants.SERVICE_PROP_COMPONENT_VERSION);

    return new ComponentNodeIdBaseData(tmpComponentId, tmpVersion,
        serviceReference.getBundle().getBundleId());

  }

  public final long bundleId;

  public final String componentId;

  public final Version componentVersion;

  public ComponentNodeIdBaseData(final String componentId, final Version componentVersion,
      final long bundleId) {
    this.componentId = componentId;
    this.componentVersion = componentVersion;
    this.bundleId = bundleId;
  }
}
