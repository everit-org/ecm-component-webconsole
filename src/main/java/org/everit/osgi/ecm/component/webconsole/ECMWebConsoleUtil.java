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

import java.util.Map;

import org.everit.osgi.ecm.component.ECMComponentConstants;
import org.everit.osgi.ecm.component.resource.ComponentRevision;
import org.osgi.framework.Constants;

/**
 * Utility class for ECM web console.
 *
 */
public class ECMWebConsoleUtil {

  private String getComponentId(final Map<String, Object> attributes) {
    return (String) attributes.get(ECMComponentConstants.SERVICE_PROP_COMPONENT_ID);
  }

  private String getComponentServicePID(final Map<String, Object> attributes) {
    return (String) attributes.get(Constants.SERVICE_PID);
  }

  private String getEcmId(final Map<String, Object> attributes) {
    String ecmId = getComponentServicePID(attributes);
    if (ecmId == null) {
      ecmId = getComponentId(attributes);
    }
    return ecmId;
  }

  /**
   * Returns the ECM Id of a component, if it is an ECM component.
   *
   * @param revision
   *          target component
   * @return If the component is a factory created component revision, then the service PID is
   *         returned. If it is a simple component, it's component Id is returned. If the component
   *         is not an ECM component, null is returned.
   */
  public String getId(final ComponentRevision<?> revision) {
    return getEcmId(revision.getProperties());
  }

}
