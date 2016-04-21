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

import org.everit.osgi.ecm.component.resource.ComponentState;
import org.everit.osgi.ecm.metadata.ConfigurationPolicy;

/**
 * DTO representation of an ECM component.
 */
public class ComponentNodeDTO {

  public ConfigurationPolicy configurationPolicy;

  public String description;

  public String name;

  public String nodeId;

  public AttributeMap properties;

  public ComponentRequirementDTO[] requirements;

  public ComponentState state;

}
