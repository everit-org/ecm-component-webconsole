package org.everit.osgi.ecm.component.webconsole.graph;

import org.everit.osgi.ecm.component.resource.ComponentState;
import org.everit.osgi.ecm.metadata.ConfigurationPolicy;

public class ComponentNodeDTO {

  public ConfigurationPolicy configurationPolicy;

  public String label;

  public String nodeId;

  public ComponentRequirementDTO[] requirements;

  public ComponentState state;

  public String title;
}
