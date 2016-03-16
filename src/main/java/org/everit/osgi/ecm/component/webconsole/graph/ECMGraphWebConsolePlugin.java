package org.everit.osgi.ecm.component.webconsole.graph;

import java.io.IOException;
import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.everit.osgi.ecm.component.resource.ComponentContainer;
import org.everit.web.servlet.HttpServlet;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

public class ECMGraphWebConsolePlugin extends HttpServlet {

  private ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker;

  private BundleContext context;

  public ECMGraphWebConsolePlugin(
      final ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker,
      final BundleContext context) {
    this.containerTracker = containerTracker;
    this.context = context;
  }

  public URL getResource(final String path) {
    int indexOfRes = path.indexOf("/res/");
    if (indexOfRes < 0) {
      return null;
    }
    String resourcePath = "META-INF/webcontent/resources/" + path.substring(indexOfRes + 5);
    return ECMGraphWebConsolePlugin.class.getClassLoader().getResource(resourcePath);
  }

  @Override
  protected void service(final HttpServletRequest arg0, final HttpServletResponse arg1)
      throws ServletException, IOException {
    // TODO Auto-generated method stub

  }

}
