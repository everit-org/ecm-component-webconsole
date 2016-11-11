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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

/**
 * Generates an ECM Graph that can be rendered.
 */
public final class ECMGraphGenerator {

  /**
   * A service capability that is not wired, but it would be if the component was active.
   */
  private static class GuessedServiceCapability {
    public String nodeId;

    Set<String> objectclasses;

    Map<String, Object> properties;
  }

  /**
   * Generates the ECM graph.
   *
   * @param containerTracker
   *          Tracks the {@link ComponentContainer}s.
   * @return The graph.
   */
  public static ECMGraphDTO generate(
      final ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker,
      final Filter filter) {

    ECMGraphGenerator generator = new ECMGraphGenerator(containerTracker, filter);
    return generator.generate();
  }

  private final Map<String, CapabilityNodeDTO> capabilityNodes = new HashMap<>();

  private final Set<ComponentNodeDTO> componentNodes = new HashSet<>();

  private final ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker;

  private Filter filter;

  private final List<GuessedServiceCapability> guessedServiceCapabilities =
      new ArrayList<>();

  private final Map<BundleCapability, CapabilityNodeDTO> processedBundleCapabilities =
      new HashMap<>();

  private final Map<ComponentRequirementDTO, ComponentRequirement<?, ?>> unsatisfiedRequirements =
      new HashMap<>();

  private ECMGraphGenerator(
      final ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker,
      final Filter filter) {
    this.containerTracker = containerTracker;
    this.filter = filter;
  }

  private void addImplementedInterfacesToSet(final Class<?> clazz, final Set<String> result) {
    if (clazz.isInterface()) {
      result.add(clazz.getName());
    } else {
      Class<?> superclass = clazz.getSuperclass();
      if (superclass != null) {
        addImplementedInterfacesToSet(superclass, result);
      }
    }

    Class<?>[] interfaces = clazz.getInterfaces();
    for (Class<?> interfaze : interfaces) {
      addImplementedInterfacesToSet(interfaze, result);
    }

  }

  private boolean addServiceInstanceClassesToServiceSet(final ServiceMetadata service,
      final Set<Set<String>> result, final ComponentMetadata componentMetadata,
      final ClassLoader componentClassLoader, final boolean pEmptyClassArrayProcessed) {
    boolean emptyClassArrayProcessed = pEmptyClassArrayProcessed;
    String[] clazzNames = service.getClazzes();
    Set<String> serviceInterfaces = new LinkedHashSet<>();
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
      addImplementedInterfacesToSet(componentClass, serviceInterfaces);

      // No interface is implemented and defined, so add the type of the class
      if (serviceInterfaces.size() == 0) {
        serviceInterfaces.add(componentClass.getName());
      }
      emptyClassArrayProcessed = true;
    } else {
      serviceInterfaces.addAll(Arrays.asList(clazzNames));
    }
    result.add(serviceInterfaces);
    return emptyClassArrayProcessed;
  }

  private String findGuessedServiceCapability(
      final ComponentRequirement<?, ?> componentRequirement) {

    Iterator<GuessedServiceCapability> iterator = guessedServiceCapabilities.iterator();

    String capabilityNodeId = null;
    while ((capabilityNodeId == null) && iterator.hasNext()) {
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
      ComponentContainer<?> componentContainer = trackedContainer.getValue();
      ComponentRevision<?>[] componentRevisions = componentContainer.getResources();
      for (ComponentRevision<?> componentRevision : componentRevisions) {
        processComponentRevision(componentRevision,
            ComponentNodeIdBaseData.createByServiceRef(serviceReference));
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
    result.nodeId = "bundleCapability." + bundleCapability.getRevision().getBundle().getBundleId()
        + resolveIndexOfBundleCapability(bundleCapability);
    result.capabilityType = CapabilityType.BUNDLE_CAPABILITY;
    result.namespace = bundleCapability.getNamespace();
    result.attributes = new AttributeMap(bundleCapability.getAttributes());
    result.directives = Collections.emptyMap();
    result.bundleId = bundleCapability.getRevision().getBundle().getBundleId();

    putCapabilityNode(result);
    return result;
  }

  private void processComponentCapabilities(final ComponentRevision<?> componentRevision,
      final ComponentNodeIdBaseData componentNodeIdBaseData) {

    ComponentState componentState = componentRevision.getState();
    List<Capability> capabilities = componentRevision.getCapabilities(null);

    Set<Set<String>> activeServiceClasses = new HashSet<>();
    for (Capability capability : capabilities) {
      if (capability instanceof ServiceCapability) {
        ServiceCapability serviceCapability = (ServiceCapability) capability;
        ServiceReference<?> serviceReference = serviceCapability.getServiceReference();
        CapabilityNodeDTO capabilityNodeDTO =
            processServiceReference(serviceReference);
        capabilityNodeDTO.componentState = componentState;
        capabilityNodeDTO.guessed = false;

        Set<String> serviceClasses = new LinkedHashSet<>();
        String[] objectClass = (String[]) serviceReference.getProperty(Constants.OBJECTCLASS);
        serviceClasses.addAll(Arrays.asList(objectClass));
        activeServiceClasses.add(serviceClasses);
      }
    }

    if (componentState != ComponentState.ACTIVE) {
      ComponentMetadata componentMetadata =
          componentRevision.getComponentContainer().getComponentMetadata();

      Set<Set<String>> guessedServiceInstanceClasses =
          resolveGuessedServiceClasses(componentMetadata, componentRevision
              .getDeclaringResource().getBundle().adapt(BundleWiring.class).getClassLoader());

      int guessedServiceCounter = 0;
      for (Set<String> serviceClasses : guessedServiceInstanceClasses) {
        if (!activeServiceClasses.contains(serviceClasses)) {
          CapabilityNodeDTO capabilityNode = new CapabilityNodeDTO();
          Map<String, Object> componentProperties = componentRevision.getProperties();
          String componentNodeId =
              resolveComponentNodeId(componentNodeIdBaseData,
                  componentProperties.get(Constants.SERVICE_PID));

          capabilityNode.nodeId = "guessedService." + componentNodeId + guessedServiceCounter++;
          capabilityNode.componentNodeId = componentNodeId;
          capabilityNode.componentState = componentState;
          capabilityNode.namespace = "osgi.service";
          capabilityNode.guessed = true;

          Map<String, Object> attributes = new LinkedHashMap<>();

          attributes.put(Constants.OBJECTCLASS,
              serviceClasses.toArray(new String[0]));

          attributes.putAll(componentProperties);
          Map<String, String> directives = new HashMap<>();
          directives.put(Constants.EFFECTIVE_DIRECTIVE, "guess");
          capabilityNode.attributes = new AttributeMap(attributes);
          capabilityNode.directives = directives;

          capabilityNode.capabilityType = CapabilityType.SERVICE;
          putCapabilityNode(capabilityNode);

          GuessedServiceCapability guessedserviceCapability = new GuessedServiceCapability();
          guessedserviceCapability.objectclasses = serviceClasses;
          guessedserviceCapability.properties = attributes;
          guessedserviceCapability.nodeId = capabilityNode.nodeId;
          guessedServiceCapabilities.add(guessedserviceCapability);
        }
      }
    }
  }

  private ComponentRequirementDTO processComponentRequirement(
      final ComponentRequirement<?, ?> componentRequirement) {

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
        if (filter != null && !filter.matches(serviceCapability.getAttributes())) {
          return null;
        }
        result.capabilityNodeId =
            processServiceReference(serviceCapability.getServiceReference()).nodeId;
      } else if (capability instanceof BundleCapability) {
        result.acceptedCapabilityType = CapabilityType.BUNDLE_CAPABILITY;
        BundleCapability bundleCapability = (BundleCapability) capability;
        if (filter != null && !filter.matches(bundleCapability.getAttributes())) {
          return null;
        }
        result.capabilityNodeId = processBundleCapability(bundleCapability).nodeId;
      }
    }

    return result;
  }

  private ComponentRequirementDTO[] processComponentRequirements(
      final ComponentRevision<?> componentRevision) {

    List<ComponentRequirementDTO> result = new ArrayList<>();
    List<Requirement> requirements = componentRevision.getRequirements(null);
    for (Requirement requirement : requirements) {
      if (requirement instanceof ComponentRequirement) {
        ComponentRequirement<?, ?> componentRequirement = (ComponentRequirement<?, ?>) requirement;
        ComponentRequirementDTO componentRequirementDTO =
            processComponentRequirement(componentRequirement);

        if (componentRequirementDTO != null) {
          result.add(componentRequirementDTO);
        }
      }
    }
    return result.toArray(new ComponentRequirementDTO[result.size()]);
  }

  private void processComponentRevision(final ComponentRevision<?> componentRevision,
      final ComponentNodeIdBaseData componentNodeIdBaseData) {
    ComponentNodeDTO componentNode = new ComponentNodeDTO();
    ComponentContainer<?> componentContainer = componentRevision.getComponentContainer();

    componentNode.configurationPolicy =
        componentContainer.getComponentMetadata().getConfigurationPolicy();

    Map<String, Object> componentProperties = componentRevision.getProperties();

    componentNode.nodeId = resolveComponentNodeId(componentNodeIdBaseData,
        componentProperties.get(Constants.SERVICE_PID));

    componentNode.state = componentRevision.getState();
    componentNode.properties = new AttributeMap(componentRevision.getProperties());

    // FIXME Locale is hardcoded and metatype provider should be retrieved in a typesafe way.
    MetaTypeProvider metatypeProvider = (MetaTypeProvider) componentContainer;
    ObjectClassDefinition objectClassDefinition =
        metatypeProvider.getObjectClassDefinition(null, Locale.getDefault().toString());
    componentNode.name = objectClassDefinition.getName();
    componentNode.description = objectClassDefinition.getDescription();

    processComponentCapabilities(componentRevision, componentNodeIdBaseData);

    if ((filter == null) || (filter.matches(componentNode.properties))) {
      componentNode.requirements =
          processComponentRequirements(componentRevision);
      componentNodes.add(componentNode);
    }
  }

  private CapabilityNodeDTO processServiceReference(final ServiceReference<?> serviceReference) {
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
    capabilityNode.componentNodeId =
        resolveComponentNodeId(ComponentNodeIdBaseData.createByServiceRef(serviceReference),
            serviceReference.getProperty(Constants.SERVICE_PID));

    Map<String, Object> attributes = new TreeMap<>();
    String[] propertyKeys = serviceReference.getPropertyKeys();
    for (String propertyKey : propertyKeys) {
      attributes.put(propertyKey, serviceReference.getProperty(propertyKey));
    }
    capabilityNode.attributes = new AttributeMap(attributes);
    capabilityNode.directives = Collections.emptyMap();

    putCapabilityNode(capabilityNode);
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

  private void putCapabilityNode(final CapabilityNodeDTO result) {
    if ((filter == null) || (filter.matches(result.attributes))) {
      capabilityNodes.put(result.nodeId, result);
    }
  }

  private String resolveComponentNodeId(final ComponentNodeIdBaseData componentNodeIdBaseData,
      final Object servicePid) {
    if (componentNodeIdBaseData == null) {
      return null;
    }
    StringBuilder sb =
        new StringBuilder("component/bundle_").append(componentNodeIdBaseData.bundleId)
            .append("/").append(componentNodeIdBaseData.componentId).append(':')
            .append(componentNodeIdBaseData.componentVersion);
    if (servicePid != null) {
      sb.append('/').append(servicePid);
    }
    return sb.toString();
  }

  private Set<Set<String>> resolveGuessedServiceClasses(final ComponentMetadata componentMetadata,
      final ClassLoader componentClassLoader) {

    Set<Set<String>> result = new LinkedHashSet<>();
    ServiceMetadata service = componentMetadata.getService();

    boolean emptyClassArrayProcessed = false;

    if (service != null) {
      emptyClassArrayProcessed =
          addServiceInstanceClassesToServiceSet(service, result, componentMetadata,
              componentClassLoader, false);
    }

    ServiceMetadata[] manualServices = componentMetadata.getManualServices();
    for (ServiceMetadata manualService : manualServices) {
      addServiceInstanceClassesToServiceSet(manualService, result, componentMetadata,
          componentClassLoader, emptyClassArrayProcessed);
    }

    return result;
  }

  private int resolveIndexOfBundleCapability(final BundleCapability bundleCapability) {
    List<Capability> capabilities = bundleCapability.getRevision().getCapabilities(null);
    int result = -1;
    int i = 0;
    Iterator<Capability> iterator = capabilities.iterator();
    while ((result == -1) && iterator.hasNext()) {
      Capability capability = iterator.next();
      if (capability.equals(bundleCapability)) {
        result = i;
      } else {
        i++;
      }
    }
    return i;
  }
}
