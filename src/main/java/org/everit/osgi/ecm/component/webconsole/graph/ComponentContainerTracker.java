package org.everit.osgi.ecm.component.webconsole.graph;

import org.everit.osgi.ecm.component.resource.ComponentContainer;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

public class ComponentContainerTracker
    extends ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> {

  public ComponentContainerTracker(final BundleContext bundleContext) {
    super(bundleContext, ComponentContainer.class.getName(), null);
  }
}
