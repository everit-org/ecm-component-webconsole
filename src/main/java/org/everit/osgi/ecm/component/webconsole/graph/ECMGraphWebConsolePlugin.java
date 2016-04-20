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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.everit.expression.ExpressionCompiler;
import org.everit.expression.ParserConfiguration;
import org.everit.expression.jexl.JexlExpressionCompiler;
import org.everit.osgi.ecm.component.resource.ComponentContainer;
import org.everit.osgi.ecm.component.webconsole.StreamUtil;
import org.everit.templating.CompiledTemplate;
import org.everit.templating.TemplateCompiler;
import org.everit.templating.html.HTMLTemplateCompiler;
import org.everit.templating.text.TextTemplateCompiler;
import org.everit.web.servlet.HttpServlet;
import org.osgi.util.tracker.ServiceTracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * A webconsole plugin that draws ECM Components as a graph.
 */
public class ECMGraphWebConsolePlugin extends HttpServlet {

  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

  private static final CompiledTemplate HTML_TEMPLATE;

  public static final String LABEL = "everit_ecm_component_graph";

  public static final int PLUGIN_ROOT_LENGTH_IN_PATHINFO;

  static {
    PLUGIN_ROOT_LENGTH_IN_PATHINFO = LABEL.length() + 1;

    ExpressionCompiler expressionCompiler = new JexlExpressionCompiler();
    TemplateCompiler textTemplateCompiler = new TextTemplateCompiler(expressionCompiler);

    Map<String, TemplateCompiler> inlineCompilers = new HashMap<>();
    inlineCompilers.put("text", textTemplateCompiler);

    TemplateCompiler htmlTemplateCompiler =
        new HTMLTemplateCompiler(expressionCompiler, inlineCompilers);

    ClassLoader classLoader = ECMGraphWebConsolePlugin.class.getClassLoader();
    ParserConfiguration parserConfiguration = new ParserConfiguration(classLoader);

    String templateName = "META-INF/webcontent/ecm_graph.html";
    parserConfiguration.setName(templateName);
    HTML_TEMPLATE = htmlTemplateCompiler.compile(StreamUtil.readContent(
        classLoader.getResourceAsStream("META-INF/webcontent/ecm_graph.html")),
        parserConfiguration);
  }

  private final ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker;

  public ECMGraphWebConsolePlugin(
      final ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker) {
    this.containerTracker = containerTracker;
  }

  /**
   * Gets the resource that belongs to the plugin.
   *
   * @param path
   *          the path of the resource.
   * @return The URL of the resource.
   */
  public URL getResource(final String path) {
    int indexOfRes = path.indexOf("/res/");
    if (indexOfRes < 0) {
      return null;
    }
    final int lengthOfSlashResSlash = 5;
    String resourcePath =
        "META-INF/webcontent/resources/" + path.substring(indexOfRes + lengthOfSlashResSlash);

    return ECMGraphWebConsolePlugin.class.getClassLoader().getResource(resourcePath);
  }

  private void renderGraphJson(final HttpServletResponse resp)
      throws IOException {
    resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
    resp.setContentType("application/json");
    ECMGraphDTO ecmGraph = ECMGraphGenerator.generate(containerTracker);

    String json = GSON.toJson(ecmGraph);
    resp.getWriter().write(json);
  }

  @Override
  protected void service(final HttpServletRequest req, final HttpServletResponse resp)
      throws ServletException, IOException {

    String pathInfo = req.getPathInfo().substring(PLUGIN_ROOT_LENGTH_IN_PATHINFO);

    if ("/graph.json".equals(pathInfo)) {
      renderGraphJson(resp);
      return;
    }

    Map<String, Object> vars = new HashMap<>();
    vars.put("appRoot", req.getAttribute("felix.webconsole.appRoot"));
    vars.put("pluginRoot", req.getAttribute("felix.webconsole.pluginRoot"));

    HTML_TEMPLATE.render(resp.getWriter(), vars);
  }

}
