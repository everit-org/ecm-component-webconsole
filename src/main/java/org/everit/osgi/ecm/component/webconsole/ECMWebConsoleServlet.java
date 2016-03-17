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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.everit.expression.ExpressionCompiler;
import org.everit.expression.ParserConfiguration;
import org.everit.expression.jexl.JexlExpressionCompiler;
import org.everit.osgi.ecm.component.ECMComponentConstants;
import org.everit.osgi.ecm.component.resource.ComponentContainer;
import org.everit.osgi.ecm.component.resource.ComponentRevision;
import org.everit.osgi.ecm.component.resource.ComponentState;
import org.everit.templating.CompiledTemplate;
import org.everit.templating.TemplateCompiler;
import org.everit.templating.html.HTMLTemplateCompiler;
import org.everit.templating.text.TextTemplateCompiler;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.metatype.MetaTypeProvider;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Webconsole plugin servlet that shows all ECM components and their states.
 */
public class ECMWebConsoleServlet implements Servlet {

  private final class HTMLClauseAttributeValueConverter
      implements ClauseAttributeValueConverter {

    @Override
    public String toString(final String key, final Object value) {
      if (key.equals(Constants.SERVICE_ID)) {
        return "<a href=\"services/" + value + "\" class=\"reqCapLink\">" + value
            + "</a>";
      } else if (key
          .equals(ECMComponentConstants.SERVICE_PROP_COMPONENT_SERVICE_PID)) {
        return "<a href=\"#" + value
            + "\" class=\"reqCapLink\" onclick=\"componentLinkClick(event, '" + value
            + "');return false;\">" + value + "</a>";
      } else if (!isEcmFactory
          && key.equals(ECMComponentConstants.SERVICE_PROP_COMPONENT_ID)) {
        sb.append("<a href=\"#" + clauseEntry.getValue()
            + "\" class=\"reqCapLink\" onclick=\"componentLinkClick(event, '"
            + value + "');return false;\">"
            + ECMComponentConstants.SERVICE_PROP_COMPONENT_ID + "="
            + value + "</a>");
      } else {
        return String.valueOf(value);
      }
    }
  }

  private static final ExceptionFormatter EXCEPTION_FORMATTER = new ExceptionFormatter();

  /**
   * In case there is a fragment suffix in the end of the URI, only the specified
   * {@link ComponentRevision} is rendered.
   */
  private static final String FRAGMENT_URI_SUFFIX = ".fragment";

  private static final int HTTP_NOT_FOUND = 404;

  private final BundleContext bundleContext;

  private final ClassLoader classLoader;

  private final CompiledTemplate componentsTemplate;

  private final ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker;

  private ServletConfig servletConfig;

  /**
   * Constructor.
   *
   * @param containerTracker
   *          The {@link ServiceTracker} that tracks all {@link ComponentContainer} services.
   * @param bundleContext
   *          The context of the bundle that contains this webconsole plugin.
   */
  public ECMWebConsoleServlet(
      final ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker,
      final BundleContext bundleContext) {
    this.containerTracker = containerTracker;
    this.bundleContext = bundleContext;
    classLoader = bundleContext.getBundle().adapt(BundleWiring.class).getClassLoader();

    ExpressionCompiler expressionCompiler = new JexlExpressionCompiler();

    TextTemplateCompiler textTemplateCompiler = new TextTemplateCompiler(expressionCompiler);

    Map<String, TemplateCompiler> inlineCompilers = new HashMap<String, TemplateCompiler>();
    inlineCompilers.put("text", textTemplateCompiler);

    HTMLTemplateCompiler htmlTemplateCompiler = new HTMLTemplateCompiler(expressionCompiler,
        inlineCompilers);

    ParserConfiguration parserConfiguration = new ParserConfiguration(classLoader);

    Map<String, Class<?>> variableTypes = new HashMap<String, Class<?>>();
    variableTypes.put("mp", MetaTypeProvider.class);
    parserConfiguration.setVariableTypes(variableTypes);

    componentsTemplate = htmlTemplateCompiler.compile(
        StreamUtil.readContent(
            classLoader.getResourceAsStream("META-INF/webcontent/ecm_components.html")),
        parserConfiguration);

  }

  private ComponentStateSum addState(final ComponentStateSum result, final ComponentState state) {
    switch (state) {
      case ACTIVE:
        result.setActive(result.getActive() + 1);
        break;
      case UNSATISFIED:
        result.setUnsatisfied(result.getUnsatisfied() + 1);
        break;
      case FAILED:
        result.setFailed(result.getFailed() + 1);
        break;
      case STARTING:
        result.setStarting(result.getStarting() + 1);
        break;
      case STOPPING:
        result.setStopping(result.getStopping() + 1);
        break;
      case INACTIVE:
        result.setInactive(result.getInactive() + 1);
        break;
      case FAILED_PERMANENT:
        break;
      case UPDATING_CONFIGURATION:
        break;
      default:
        break;
    }
    return result;
  }

  private void addThreadViewerAvailablityToVars(final Map<String, Object> vars)
      throws ServletException {
    boolean threadViewerAvailable = false;
    try {
      threadViewerAvailable = bundleContext.getServiceReferences(Servlet.class.getName(),
          "(felix.webconsole.label=threads)") != null;
    } catch (InvalidSyntaxException e) {
      throw new ServletException(e);
    }

    vars.put("threadViewerAvailable", threadViewerAvailable);
  }

  private ComponentStateSum countStates(
      final SortedMap<ServiceReference<ComponentContainer<?>>, ComponentContainer<?>> ccMap) {
    ComponentStateSum result = new ComponentStateSum();
    for (ComponentContainer<?> cc : ccMap.values()) {
      for (ComponentRevision<?> cr : cc.getResources()) {
        result = addState(result, cr.getState());
      }
    }

    return result;
  }

  @Override
  public void destroy() {
  }

  private ComponentContainer<?> findContainerByServiceId(
      final String serviceId) {

    Set<Entry<ServiceReference<ComponentContainer<?>>, ComponentContainer<?>>> entrySet;
    entrySet = containerTracker.getTracked().entrySet();

    Iterator<Entry<ServiceReference<ComponentContainer<?>>, ComponentContainer<?>>> iterator;
    iterator = entrySet.iterator();

    ComponentContainer<?> result = null;

    while ((result == null) && iterator.hasNext()) {
      Entry<ServiceReference<ComponentContainer<?>>, ComponentContainer<?>> entry = iterator.next();
      ServiceReference<ComponentContainer<?>> serviceReference = entry.getKey();
      if (serviceId.equals(String.valueOf(serviceReference.getProperty(Constants.SERVICE_ID)))) {
        result = entry.getValue();
      }
    }
    return result;
  }

  private ComponentRevision<?> findRevision(final ComponentRevision<?>[] revisions,
      final String servicePid) {
    ComponentRevision<?> result = null;
    for (int i = 0; (i < revisions.length) && (result == null); i++) {
      ComponentRevision<?> componentRevision = revisions[i];
      if (servicePid.equals(componentRevision.getProperties().get(Constants.SERVICE_PID))) {
        result = componentRevision;
      }
    }
    return result;
  }

  @Override
  public ServletConfig getServletConfig() {
    return servletConfig;
  }

  @Override
  public String getServletInfo() {
    return "ECM Component Webconsole Plugin";
  }

  @Override
  public void init(final ServletConfig config) throws ServletException {
    servletConfig = config;

  }

  @Override
  public void service(final ServletRequest req, final ServletResponse resp)
      throws ServletException, IOException {

    HttpServletRequest httpReq = (HttpServletRequest) req;
    HttpServletResponse httpResp = (HttpServletResponse) resp;

    httpResp.setContentType("text/html");

    PrintWriter writer = httpResp.getWriter();

    String appRoot = (String) httpReq.getAttribute("felix.webconsole.appRoot");
    String pluginRoot = (String) httpReq.getAttribute("felix.webconsole.pluginRoot");

    String requestURI = httpReq.getRequestURI();

    ComponentStateSum numberOfComponetntsByState = countStates(containerTracker.getTracked());

    Map<String, Object> vars = new HashMap<String, Object>();
    vars.put("ccMap", containerTracker.getTracked());
    vars.put("appRoot", appRoot);
    vars.put("pluginRoot", pluginRoot);

    ClauseStringGenerator clauseUtil = new ClauseStringGenerator();
    clauseUtil.setAttributeValueConverter(new HTMLClauseAttributeValueConverter());

    vars.put("templateUtil", clauseUtil);
    vars.put("exceptionFormatter", EXCEPTION_FORMATTER);
    vars.put("numberOfComponetntsByState", numberOfComponetntsByState);
    vars.put("consoleUtil", new ECMWebConsoleUtil());

    if (requestURI.equals(pluginRoot)) {
      componentsTemplate.render(writer, vars, "content");
    } else if (requestURI.endsWith(FRAGMENT_URI_SUFFIX)) {
      addThreadViewerAvailablityToVars(vars);

      String serviceIdAndPid = requestURI.substring(pluginRoot.length() + 1,
          requestURI.length() - FRAGMENT_URI_SUFFIX.length());
      String[] split = serviceIdAndPid.split("\\/");

      ComponentContainer<?> container = findContainerByServiceId(split[0]);

      if (container == null) {
        httpResp.setStatus(HTTP_NOT_FOUND);
        return;
      }

      ComponentRevision<?>[] revisions = container.getResources();
      ComponentRevision<?> revision = null;
      if (split.length > 1) {
        revision = findRevision(revisions, split[1]);
      } else if (revisions.length == 1) {
        revision = revisions[0];
      }

      if (revision == null) {
        httpResp.setStatus(HTTP_NOT_FOUND);
        return;
      }

      vars.put("revision", revision);
      vars.put("container", container);

      componentsTemplate.render(writer, vars, "componentRevision");
    } else {
      httpResp.setStatus(HTTP_NOT_FOUND);
    }
  }
}
