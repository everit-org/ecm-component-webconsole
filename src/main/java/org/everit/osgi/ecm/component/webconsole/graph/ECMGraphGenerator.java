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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.everit.osgi.ecm.component.resource.ComponentContainer;
import org.everit.osgi.ecm.component.resource.ComponentRequirement;
import org.everit.osgi.ecm.component.resource.ComponentRevision;
import org.everit.osgi.ecm.component.resource.ComponentState;
import org.everit.osgi.ecm.component.webconsole.Clause2StringConverter;
import org.everit.osgi.ecm.metadata.ComponentMetadata;
import org.everit.osgi.ecm.metadata.ServiceMetadata;
import org.everit.osgi.linkage.ServiceCapability;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Wire;
import org.osgi.service.metatype.MetaTypeProvider;
import org.osgi.service.metatype.ObjectClassDefinition;
import org.osgi.util.tracker.ServiceTracker;

public class ECMGraphGenerator {

  private static class GuessedServiceCapability {
    public String nodeId;

    Set<String> objectclasses;

    Map<String, Object> properties;
  }

  public static ECMGraphDTO generate(
      final ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker) {

    ECMGraphGenerator generator = new ECMGraphGenerator(containerTracker);
    return generator.generate();
  }

  private final Map<String, CapabilityNodeDTO> capabilityNodes = new HashMap<>();

  private final Set<ComponentNodeDTO> componentNodes = new HashSet<>();

  private final ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker;

  private final List<GuessedServiceCapability> guessedServiceCapabilities =
      new ArrayList<>();

  private final Map<BundleCapability, CapabilityNodeDTO> processedBundleCapabilities =
      new HashMap<>();

  private final Map<ComponentRequirementDTO, ComponentRequirement<?, ?>> unsatisfiedRequirements =
      new HashMap<>();

  private ECMGraphGenerator(
      final ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker) {
    this.containerTracker = containerTracker;
  }

  private void addImplementedInterfacesToSet(final Class<?> componentClass,
      final Set<String> result) {

    Class<?> superclass = componentClass.getSuperclass();
    if (superclass != null) {
      addImplementedInterfacesToSet(superclass, result);
    }
    Class<?>[] interfaces = componentClass.getInterfaces();
    for (Class<?> interfaze : interfaces) {
      result.add(interfaze.getName());
      addImplementedInterfacesToSet(interfaze, result);
    }

  }

  private Collection<String> convertClassArrayToStringCollection(
      final Class<?>[] clazzes) {
    String[] classNames = new String[clazzes.length];
    for (int i = 0; i < clazzes.length; i++) {
      classNames[i] = clazzes[i].getName();
    }
    return Arrays.asList(classNames);
  }

  private String convertServiceReferenceToClause(final ServiceReference<?> serviceReference) {
    Map<String, String> directives = Collections.emptyMap();

    Map<String, Object> attributes = new TreeMap<>();
    String[] propertyKeys = serviceReference.getPropertyKeys();
    for (String propertyKey : propertyKeys) {
      attributes.put(propertyKey, serviceReference.getProperty(propertyKey));
    }

    return new Clause2StringConverter().convertClauseToString("osgi.service", attributes,
        directives);
  }

  private String findGuessedServiceCapability(
      final ComponentRequirement<?, ?> componentRequirement) {

    Iterator<GuessedServiceCapability> iterator = guessedServiceCapabilities.iterator();

    String capabilityNodeId = null;
    while (capabilityNodeId == null && iterator.hasNext()) {
      GuessedServiceCapability guessedServiceCapability = iterator.next();
      String objectClass = componentRequirement.getDirectives().get(Constants.OBJECTCLASS);
      if (guessedServiceCapability.objectclasses.contains(objectClass)) {
        String filterString = componentRequirement.getDirectives().get(Constants.FILTER_DIRECTIVE);
        if (filterString == null) {
          capabilityNodeId = guessedServiceCapability.nodeId;
        } else {
          try {
            Filter filter = FrameworkUtil.createFilter(filterString);
            if (filter.matches(guessedServiceCapability.properties)) {
              capabilityNodeId = guessedServiceCapability.nodeId;
            }
          } catch (InvalidSyntaxException e) {
            capabilityNodeId = null;
          }
        }
      }
    }
    return capabilityNodeId;
  }

  private ECMGraphDTO generate() {
    SortedMap<ServiceReference<ComponentContainer<?>>, ComponentContainer<?>> trackedContainers =
        containerTracker.getTracked();

    for (Entry<ServiceReference<ComponentContainer<?>>, ComponentContainer<?>> trackedContainer : trackedContainers // CS_DISABLE_LINE_LENGTH
        .entrySet()) {
      ServiceReference<ComponentContainer<?>> serviceReference = trackedContainer.getKey();
      Object containerServiceId = serviceReference.getProperty(Constants.SERVICE_ID);
      ComponentContainer<?> componentContainer = trackedContainer.getValue();
      ComponentRevision<?>[] componentRevisions = componentContainer.getResources();
      for (ComponentRevision<?> componentRevision : componentRevisions) {
        processComponentRevision(componentRevision, containerServiceId);
      }
    }

    processUnsatisfiedRequirements();

    ECMGraphDTO graph = new ECMGraphDTO();
    graph.components = componentNodes.toArray(new ComponentNodeDTO[componentNodes.size()]);

    graph.capabilities =
        capabilityNodes.values().toArray(new CapabilityNodeDTO[capabilityNodes.size()]);

    return graph;
  }

  private CapabilityNodeDTO processBundleCapability(final BundleCapability bundleCapability) {
    CapabilityNodeDTO result = processedBundleCapabilities.get(bundleCapability);
    if (result != null) {
      return result;
    }
    result = new CapabilityNodeDTO();
    result.capabilityType = CapabilityType.BUNDLE_CAPABILITY;
    result.namespace = bundleCapability.getNamespace();
    result.nodeId = "bundleCapability." + bundleCapability.getRevision().getBundle().getBundleId()
        + resolveIndexOfBundleCapability(bundleCapability);
    return result;
  }

  private void processComponentCapabilities(final ComponentRevision<?> componentRevision,
      final Object containerServiceId) {

    ComponentState componentState = componentRevision.getState();
    List<Capability> capabilities = componentRevision.getCapabilities(null);
    for (Capability capability : capabilities) {
      if (capability instanceof ServiceCapability) {
        ServiceCapability serviceCapability = (ServiceCapability) capability;
        ServiceReference<?> serviceReference = serviceCapability.getServiceReference();
        CapabilityNodeDTO capabilityNodeDTO =
            processServiceReference(serviceReference, containerServiceId);
        capabilityNodeDTO.componentState = componentState;
      }
    }

    if (componentState == ComponentState.UNSATISFIED || componentState == ComponentState.FAILED) {
      ComponentMetadata componentMetadata =
          componentRevision.getComponentContainer().getComponentMetadata();

      Set<String> serviceClasses = resolveServiceClasses(componentMetadata, componentRevision
          .getDeclaringResource().getBundle().adapt(BundleWiring.class).getClassLoader());
      if (serviceClasses.size() > 0) {

        CapabilityNodeDTO capabilityNode = new CapabilityNodeDTO();
        Map<String, Object> componentProperties = componentRevision.getProperties();
        String componentNodeId = resolveComponentNodeId(containerServiceId,
            componentProperties.get(Constants.SERVICE_PID));

        capabilityNode.nodeId = "guessedService." + componentNodeId;
        capabilityNode.componentNodeId = componentNodeId;
        capabilityNode.componentState = componentState;
        capabilityNode.namespace = "osgi.service";

        Map<String, Object> attributes = new LinkedHashMap<>();

        attributes.put(Constants.OBJECTCLASS,
            serviceClasses.toArray(new String[serviceClasses.size()]));

        attributes.putAll(componentProperties);
        Map<String, String> directives = new HashMap<>();
        directives.put(Constants.EFFECTIVE_DIRECTIVE, "guess");
        capabilityNode.clause = new Clause2StringConverter().convertClauseToString("osgi.service",
            componentProperties, directives);

        capabilityNode.capabilityType = CapabilityType.SERVICE;
        capabilityNodes.put(capabilityNode.nodeId, capabilityNode);

        GuessedServiceCapability guessedserviceCapability = new GuessedServiceCapability();
        guessedserviceCapability.objectclasses = serviceClasses;
        guessedserviceCapability.properties = attributes;
        guessedserviceCapability.nodeId = capabilityNode.nodeId;
        guessedServiceCapabilities.add(guessedserviceCapability);
      }
    }
  }

  private ComponentRequirementDTO processComponentRequirement(
      final ComponentRequirement<?, ?> componentRequirement, final Object containerServiceId) {

    ComponentRequirementDTO result = new ComponentRequirementDTO();
    result.requirementId = componentRequirement.getRequirementId();

    Wire[] wires = componentRequirement.getResource().getComponentContainer()
        .getWiresByRequirement(componentRequirement);

    result.clause =
        new Clause2StringConverter().convertClauseToString(componentRequirement.getNamespace(),
            componentRequirement.getAttributes(), componentRequirement.getDirectives());

    if (wires.length == 0) {
      unsatisfiedRequirements.put(result, componentRequirement);
    } else {
      result.satisfactionState = SatisfactionState.SATISFIED;
      Wire wire = wires[0];
      Capability capability = wire.getCapability();
      if (capability instanceof ServiceCapability) {
        result.acceptedCapabilityType = CapabilityType.SERVICE;
        ServiceCapability serviceCapability = (ServiceCapability) capability;
        result.capabilityNodeId =
            processServiceReference(serviceCapability.getServiceReference(),
                containerServiceId).nodeId;
      } else if (capability instanceof BundleCapability) {
        result.acceptedCapabilityType = CapabilityType.BUNDLE_CAPABILITY;
        BundleCapability bundleCapability = (BundleCapability) capability;
        result.capabilityNodeId = processBundleCapability(bundleCapability).nodeId;
      }
    }

    return result;
  }

  private ComponentRequirementDTO[] processComponentRequirements(
      final ComponentRevision<?> componentRevision, final Object containerServiceId) {

    List<ComponentRequirementDTO> result = new ArrayList<>();
    List<Requirement> requirements = componentRevision.getRequirements(null);
    for (Requirement requirement : requirements) {
      if (requirement instanceof ComponentRequirement) {
        ComponentRequirement<?, ?> componentRequirement = (ComponentRequirement<?, ?>) requirement;
        result.add(processComponentRequirement(componentRequirement, containerServiceId));
      }
    }
    return result.toArray(new ComponentRequirementDTO[result.size()]);
  }

  private void processComponentRevision(final ComponentRevision<?> componentRevision,
      final Object containerServiceId) {
    ComponentNodeDTO componentNode = new ComponentNodeDTO();
    ComponentContainer<?> componentContainer = componentRevision.getComponentContainer();

    componentNode.configurationPolicy =
        componentContainer.getComponentMetadata().getConfigurationPolicy();

    Map<String, Object> componentProperties = componentRevision.getProperties();

    componentNode.nodeId = resolveComponentNodeId(
        containerServiceId,
        componentProperties.get(Constants.SERVICE_PID));

    componentNode.state = componentRevision.getState();

    // FIXME Locale is hardcoded and metatype provider should be retrieved in a typesafe way.
    MetaTypeProvider metatypeProvider = (MetaTypeProvider) componentContainer;
    ObjectClassDefinition objectClassDefinition =
        metatypeProvider.getObjectClassDefinition(null, Locale.getDefault().toString());
    componentNode.name = objectClassDefinition.getName();

    processComponentCapabilities(componentRevision, containerServiceId);
    componentNode.requirements =
        processComponentRequirements(componentRevision, containerServiceId);

    componentNodes.add(componentNode);
  }

  private CapabilityNodeDTO processServiceReference(final ServiceReference<?> serviceReference,
      final Object containerServiceId) {
    Object serviceId = String.valueOf(serviceReference.getProperty(Constants.SERVICE_ID));
    String nodeId = "service." + serviceId;
    CapabilityNodeDTO capabilityNode = capabilityNodes.get(nodeId);
    if (capabilityNode != null) {
      return capabilityNode;
    }

    capabilityNode = new CapabilityNodeDTO();
    capabilityNode.nodeId = nodeId;
    capabilityNode.capabilityType = CapabilityType.SERVICE;
    capabilityNode.namespace = "osgi.service";
    capabilityNode.componentNodeId = resolveComponentNodeId(containerServiceId,
        serviceReference.getProperty(Constants.SERVICE_PID));

    capabilityNode.clause = convertServiceReferenceToClause(serviceReference);

    capabilityNodes.put(nodeId, capabilityNode);
    return capabilityNode;
  }

  private void processUnsatisfiedRequirement(final ComponentRequirement<?, ?> componentRequirement,
      final ComponentRequirementDTO componentRequirementDTO) {

    Class<?> acceptedCapabilityType = componentRequirement.getAcceptedCapabilityType();
    if (acceptedCapabilityType.equals(BundleCapability.class)) {
      componentRequirementDTO.acceptedCapabilityType = CapabilityType.BUNDLE_CAPABILITY;
      componentRequirementDTO.satisfactionState = SatisfactionState.UNSATISFIED;
    } else if (acceptedCapabilityType.equals(ServiceCapability.class)) {
      componentRequirementDTO.acceptedCapabilityType = CapabilityType.SERVICE;

      String capabilityNodeId = findGuessedServiceCapability(componentRequirement);
      if (capabilityNodeId != null) {
        componentRequirementDTO.capabilityNodeId = capabilityNodeId;
        componentRequirementDTO.satisfactionState = SatisfactionState.GUESSED;
      } else {
        componentRequirementDTO.satisfactionState = SatisfactionState.UNSATISFIED;
      }
    }
  }

  private void processUnsatisfiedRequirements() {
    Set<Entry<ComponentRequirementDTO, ComponentRequirement<?, ?>>> entrySet =
        unsatisfiedRequirements.entrySet();

    for (Entry<ComponentRequirementDTO, ComponentRequirement<?, ?>> entry : entrySet) {
      processUnsatisfiedRequirement(entry.getValue(), entry.getKey());
    }
  }

  private String resolveComponentNodeId(final Object componentContainerServiceId,
      final Object servicePid) {
    if (componentContainerServiceId == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder("component.").append(componentContainerServiceId);
    if (servicePid != null) {
      sb.append('.').append(servicePid);
    }
    return sb.toString();
  }

  private int resolveIndexOfBundleCapability(final BundleCapability bundleCapability) {
    List<Capability> capabilities = bundleCapability.getRevision().getCapabilities(null);
    int result = -1;
    int i = 0;
    Iterator<Capability> iterator = capabilities.iterator();
    while (result == -1 && iterator.hasNext()) {
      Capability capability = iterator.next();
      if (capability.equals(bundleCapability)) {
        result = i;
      } else {
        i++;
      }
    }
    return i;
  }

  private Set<String> resolveServiceClasses(final ComponentMetadata componentMetadata,
      final ClassLoader componentClassLoader) {
    ServiceMetadata service = componentMetadata.getService();
    ServiceMetadata manualService = componentMetadata.getManualService();
    if (service == null && manualService == null) {
      return Collections.emptySet();
    }
    Set<String> result = new HashSet<>();
    if (service != null) {
      String[] clazzNames = service.getClazzes();
      if (clazzNames.length == 0) {
        String componentType = componentMetadata.getType();
        Class<?> componentClass;
        try {
          componentClass = componentClassLoader.loadClass(componentType);
        } catch (ClassNotFoundException e) {
          throw new RuntimeException("The class " + componentType
              + " cannot be loaded by the classloader of the bundle of the component: "
              + componentClassLoader, e);
        }
        addImplementedInterfacesToSet(componentClass, result);
      } else {
        result.addAll(Arrays.asList(clazzNames));
      }
    }
    if (manualService != null) {
      result.addAll(Arrays.asList(manualService.getClazzes()));
    }
    return result;
  }
}
