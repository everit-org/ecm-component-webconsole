package org.everit.osgi.ecm.component.webconsole.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.everit.osgi.ecm.component.ECMComponentConstants;
import org.everit.osgi.ecm.component.resource.ComponentContainer;
import org.everit.osgi.ecm.component.resource.ComponentRequirement;
import org.everit.osgi.ecm.component.resource.ComponentRevision;
import org.everit.osgi.linkage.ServiceCapability;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Wire;
import org.osgi.util.tracker.ServiceTracker;

public class ECMGraphGenerator {

  public static ECMGraphDTO generate(
      final ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker) {

    ECMGraphGenerator generator = new ECMGraphGenerator(containerTracker);
    return generator.generate();
  }

  private final Map<String, CapabilityNodeDTO> capabilityNodes = new HashMap<>();

  private final Set<ComponentNodeDTO> componentNodes = new HashSet<>();

  private final ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker;

  private final List<NotRegisteredServiceCapability> notOfferedServiceCapabilities =
      new ArrayList<>();

  private final Map<BundleCapability, CapabilityNodeDTO> processedBundleCapabilities =
      new HashMap<>();

  private final Map<ComponentRequirementDTO, ComponentRequirement<?, ?>> unsatisfiedRequirements =
      new HashMap<>();

  private ECMGraphGenerator(
      final ServiceTracker<ComponentContainer<?>, ComponentContainer<?>> containerTracker) {
    this.containerTracker = containerTracker;
  }

  private ECMGraphDTO generate() {
    ComponentContainer<?>[] componentContainers =
        containerTracker.getServices(new ComponentContainer[0]);

    for (ComponentContainer<?> componentContainer : componentContainers) {
      ComponentRevision<?>[] componentRevisions = componentContainer.getResources();
      for (ComponentRevision<?> componentRevision : componentRevisions) {
        processComponentRevision(componentRevision);
      }
    }

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
    result.nodeId = "bundleCapability." + bundleCapability.getRevision().getBundle().getBundleId()
        + resolveIndexOfBundleCapability(bundleCapability);
    return result;
  }

  private void processComponentCapabilities(final ComponentRevision<?> componentRevision) {

    List<Capability> capabilities = componentRevision.getCapabilities(null);
    for (Capability capability : capabilities) {
      if (capability instanceof ServiceCapability) {
        ServiceCapability serviceCapability = (ServiceCapability) capability;
        ServiceReference<?> serviceReference = serviceCapability.getServiceReference();
        processServiceReference(serviceReference);

      }
    }
  }

  private ComponentRequirementDTO processComponentRequirement(
      final ComponentRequirement<?, ?> componentRequirement) {

    ComponentRequirementDTO result = new ComponentRequirementDTO();
    result.requirementId = componentRequirement.getRequirementId();

    Wire[] wires = componentRequirement.getResource().getComponentContainer()
        .getWiresByRequirement(componentRequirement);

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
            processServiceReference(serviceCapability.getServiceReference()).nodeId;
      } else if (capability instanceof BundleCapability) {
        result.acceptedCapabilityType = CapabilityType.BUNDLE_CAPABILITY;
        BundleCapability bundleCapability = (BundleCapability) capability;
        result.capabilityNodeId = processBundleCapability(bundleCapability).nodeId;
      }
      // TODO
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
        result.add(processComponentRequirement(componentRequirement));
      }
    }
    return result.toArray(new ComponentRequirementDTO[result.size()]);
  }

  private void processComponentRevision(final ComponentRevision<?> componentRevision) {
    ComponentNodeDTO componentNode = new ComponentNodeDTO();
    ComponentContainer<?> componentContainer = componentRevision.getComponentContainer();

    componentNode.configurationPolicy =
        componentContainer.getComponentMetadata().getConfigurationPolicy();

    Map<String, Object> componentProperties = componentRevision.getProperties();

    componentNode.nodeId = resolveComponentNodeId(
        componentProperties.get(ECMComponentConstants.SERVICE_PROP_COMPONENT_CONTAINER_SERVICE_ID),
        componentProperties.get(Constants.SERVICE_PID));

    componentNode.state = componentRevision.getState();

    processComponentCapabilities(componentRevision);
    componentNode.requirements =
        processComponentRequirements(componentRevision);

    componentNodes.add(componentNode);
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
    capabilityNode.componentNodeId = resolveComponentNodeId(
        serviceReference
            .getProperty(ECMComponentConstants.SERVICE_PROP_COMPONENT_CONTAINER_SERVICE_ID),
        serviceReference.getProperty(Constants.SERVICE_PID));

    capabilityNodes.put(nodeId, capabilityNode);
  }

  private void processUnsatisfiedRequirement(final ComponentRequirement<?, ?> componentRequirement,
      final ComponentRequirementDTO result) {

    Class<?> acceptedCapabilityType = componentRequirement.getAcceptedCapabilityType();
    if (acceptedCapabilityType.equals(BundleCapability.class)) {
      result.acceptedCapabilityType = CapabilityType.BUNDLE_CAPABILITY;
      result.satisfactionState = SatisfactionState.UNSATISFIED;
    } else if (acceptedCapabilityType.equals(ServiceCapability.class)) {
      result.acceptedCapabilityType = CapabilityType.SERVICE;
      // TODO try getting service from bundleContext.getServiceReference();
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
}
