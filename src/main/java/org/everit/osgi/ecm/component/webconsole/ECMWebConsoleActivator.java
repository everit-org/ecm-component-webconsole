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

import java.util.Hashtable;

import javax.servlet.Servlet;

import org.everit.osgi.ecm.component.resource.ComponentContainer;
import org.everit.osgi.ecm.component.webconsole.graph.ECMGraphWebConsolePlugin;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Bundle Activator of ECM Component Webconsole plugin that registers the plugin servlet.
 */
public class ECMWebConsoleActivator implements BundleActivator {

  private ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker;

  private ServiceRegistration<Servlet> graphPluginSR;

  private ServiceRegistration<Servlet> tablePluginSR;

  private void registerGraphPlugin(final BundleContext context) {
    Hashtable<String, Object> servletProps = new Hashtable<String, Object>();
    servletProps.put("felix.webconsole.label", ECMGraphWebConsolePlugin.LABEL);
    servletProps.put("felix.webconsole.category", "Everit");
    servletProps.put("felix.webconsole.title", "ECM Component Graph (alpha)");
    servletProps.put("felix.webconsole.css", new String[] {
        "/" + ECMGraphWebConsolePlugin.LABEL + "/res/ecm.css",
        "/" + ECMGraphWebConsolePlugin.LABEL + "/res/ecm-graph.css",
        "/" + ECMGraphWebConsolePlugin.LABEL + "/res/tipsy/stylesheets/tipsy.css" });

    Servlet servlet = new ECMGraphWebConsolePlugin(containerTracker);
    graphPluginSR = context.registerService(Servlet.class, servlet, servletProps);
  }

  private void registerTablePlugin(final BundleContext context) {
    Hashtable<String, String> servletProps = new Hashtable<String, String>();
    servletProps.put("felix.webconsole.label", "everit_ecm_component");
    servletProps.put("felix.webconsole.category", "Everit");
    servletProps.put("felix.webconsole.title", "ECM Components");
    servletProps.put("felix.webconsole.css", "res/ui/config.css");

    Servlet servlet = new ECMWebConsoleServlet(containerTracker, context);
    tablePluginSR = context.registerService(Servlet.class, servlet, servletProps);
  }

  @Override
  public void start(final BundleContext context) {
    @SuppressWarnings({ "unchecked", "rawtypes" })
    Class<ComponentContainer<?>> clazz = (Class) ComponentContainer.class;

    containerTracker = new ServiceTracker<ComponentContainer<?>, ComponentContainer<?>>(context,
        clazz, null);
    containerTracker.open();

    registerTablePlugin(context);
    registerGraphPlugin(context);
  }

  @Override
  public void stop(final BundleContext context) {
    tablePluginSR.unregister();
    graphPluginSR.unregister();
    containerTracker.close();
  }

}
