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

function EcmGraph() {
  // Create the input graph
  var g = new dagreD3.graphlib.Graph().setGraph({});
  var appRoot = $('div.ecm-graph').attr('data-ecm-appRoot');
  var entityCounter = 0;
  var ecmEdges = {};
  var nodeIdByUniqueClass = {};

  var createUniqueClassForNode = function(nodeId) {
    // TODO
  }

  var createUniqueClassForEdge = function(v, w) {
    var edgeUniqueClass = 'ecm-edge-' + (entityCounter++);
    var wIdMap = ecmEdges[v];
    if (!wIdMap) {
      wIdMap = {};
      ecmEdges[v] = wIdMap;
    }
    wIdMap[w, edgeUniqueClass];
    return edgeUniqueClass;
  }

  var resolveCapabilityLabel = function(capability) {
    if (capability.capabilityType == 'SERVICE') {
      var label = '<div style="text-align: center;">Service';
      if (!capability.guessed) {
        var serviceId = capability.attributes['service.id'];
        label = label + '<br /><a href="' + appRoot + '/services/' + serviceId
            + '">' + serviceId + '</a></div>';
      } else {
        label = label + '</p>';
      }
      return $(label)[0];
    } else {
      return $('<div style="text-align: center;">Bundle Capability<br /><a href="' + appRoot
          + '/bundles/' + capability.bundleId + '">' + capability.bundleId
          + '</a></div>')[0];
    }
  }

  var htmlEscape = function(text) {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g,
        '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;');
  }

  var resolveEdgeLabel = function(edgeUniqueClass, labelText) {
    return $('<p class="' + edgeUniqueClass + '">' + htmlEscape(labelText)
        + '</p>')[0];
  }

  var convertMapToHtmlList = function(map) {
    var result = '<ul style="text-align: left; max-width: 60em; word-wrap: break-word; text-indent: -2em; margin-left: 2em;">';
    for ( var key in map) {
      if (map.hasOwnProperty(key)) {
        result = result + '<li><strong style="font-weight: bold;">'
            + htmlEscape(key) + ':</strong> '
            + htmlEscape(JSON.stringify(map[key])) + '</li>';
      }
    }
    result = result + '</ul>';
    return result;
  }

  var resolveTooltip = function(node) {
    if (node.capability) {
      if (node.capability.capabilityType == "SERVICE") {
        return convertMapToHtmlList(node.capability.attributes);
      } else {
        var label = "<div>";
        label += convertMapToHtmlList({
          namespace : node.capability.namespace
        });
        label += "<br />";
        label += convertMapToHtmlList(node.capability.attributes);
        label += "</div>";
        return label;
      }
    } else if (node.component) {
      var component = node.component;
      var label = "<div>";
      var componentMetaMap = {
        "Name" : component.name
      };
      if (component.description) {
        componentMetaMap["Description"] = component.description;
      }
      componentMetaMap["State"] = component.state;
      label += convertMapToHtmlList(componentMetaMap);
      label += "<br />";
      label += convertMapToHtmlList(component.properties);
      label += "</div>";
      return label;
    } else {
      return null;
    }
  }

  var resolveCapabilityAdditionalClasses = function(capability) {
    var additionalClasses = '';
    if (capability.componentState) {
      additionalClasses = ' componentstate-' + capability.componentState;
    } else {
      additionalClasses = ' componentstate-ACTIVE';
    }
    if (capability.guessed) {
      additionalClasses += ' guessed';
    }
    return additionalClasses;
  }

  var resolveShapeByCapabilityType = function(capabilityType) {
    if (capabilityType == 'SERVICE') {
      return 'circle';
    } else {
      return 'ellipse';
    }
  }

  var addCapabilityNodeWithEdge = function(capability) {
    var additionalClasses = resolveCapabilityAdditionalClasses(capability);
    g.setNode(capability.nodeId, {
      label : resolveCapabilityLabel(capability),
      shape : resolveShapeByCapabilityType(capability.capabilityType),
      rx : 5,
      ry : 5,
      capability : capability,
      class : 'capability' + additionalClasses
    });
    if (capability.componentNodeId) {
      var edgeUniqueClass = createUniqueClassForEdge(capability.nodeId,
          capability.componentNodeId);
      g.setEdge(capability.nodeId, capability.componentNodeId, {
        arrowhead : 'undirected',
        arrowheadClass : 'arrowhead ' + edgeUniqueClass,
        class : 'capability-line ' + edgeUniqueClass
      });
    }
  }

  var applyEcmGraphOnDagreG = function(ecmGraph) {
    entityCounter = 0;
    ecmEdges = {};
    var components = ecmGraph.components;

    for (i = 0; i < components.length; i++) {
      var component = components[i];
      var additionalClasses = ' componentstate-' + component.state;
      g.setNode(component.nodeId, {
        label : component.name,
        component : component,
        rx : 5,
        ry : 5,
        class : 'component' + additionalClasses
      });

      var requirements = component.requirements;
      for (j = 0; j < requirements.length; j++) {
        var requirement = requirements[j];

        var edgeUniqueClass = createUniqueClassForEdge(component.nodeId,
            requirement.capabilityNodeId);

        if (requirement.capabilityNodeId) {
          g.setEdge(component.nodeId, requirement.capabilityNodeId,
              {
                label : resolveEdgeLabel(edgeUniqueClass,
                    requirement.requirementId),
                arrowheadClass : 'arrowhead',
                class : 'requirement ' + edgeUniqueClass
              });
        } else {
          var missingNodeId = "missing." + component.nodeId + ".j";
          var capabilityName = (requirement.acceptedCapabilityType == "SERVICE") ? "Service"
              : "Bundle Capability";
          g
              .setNode(
                  missingNodeId,
                  {
                    label : capabilityName,
                    shape : resolveShapeByCapabilityType(requirement.acceptedCapabilityType),
                    class : "missing"
                  });

          var edgeUniqueClass = createUniqueClassForEdge(component.nodeId,
              missingNodeId);
          g.setEdge(component.nodeId, missingNodeId,
              {
                label : resolveEdgeLabel(edgeUniqueClass,
                    requirement.requirementId),
                arrowheadClass : 'arrowhead',
                class : 'requirement ' + edgeUniqueClass
              });
        }
      }
    }

    var capabilities = ecmGraph.capabilities;
    for (i = 0; i < capabilities.length; i++) {
      var capability = capabilities[i];
      addCapabilityNodeWithEdge(capability);
    }
  }

  var resizeEcmGraphSVG = function() {
    var windowHeight = $(window).height();
    var ecmGraphDivObj = $('svg.ecm-graph');
    var topOfEcmGraph = ecmGraphDivObj.offset().top;
    var ecmGraphDivHeight = windowHeight - topOfEcmGraph - 20;
    if (ecmGraphDivHeight < 300) {
      ecmGraphDivHeight = 300;
    }

    ecmGraphDivObj.height(ecmGraphDivHeight + 'px');
  }

  var clearSelection = function() {
    $('.ecm-hovering').removeClass('hovering');
    $('.ecm-visible').removeClass('.ecm-visible');
    $('.ecm-hovered').removeClass('.ecm-hovered');
  }

  // Create the renderer
  var render = new dagreD3.render();

  // Set up an SVG group so that we can translate the final graph.
  var svg = d3.select("svg.ecm-graph");
  var svgGroup = svg.append("g");

  // Set up zoom support
  var zoom = d3.behavior.zoom().on(
      "zoom",
      function() {
        svgGroup.attr("transform", "translate(" + d3.event.translate + ")"
            + "scale(" + d3.event.scale + ")");
      });
  svg.call(zoom);

  var renderECMGraph = function(data, callRender) {
    g = new dagreD3.graphlib.Graph().setGraph({});
    applyEcmGraphOnDagreG(data);

    g.graph().transition = function(selection) {
      return selection.transition().duration(500);
    };

    // Run the renderer. This is what draws the final graph.
    render(d3.select("svg.ecm-graph g"), g);

    svgGroup.selectAll("g.node").attr("title", function(v) {
      return resolveTooltip(g.node(v));
    }).each(function(v) {
      $(this).tipsy({
        gravity : "w",
        html : true
      });
    });
  }

  $(function() {
    resizeEcmGraphSVG();

    $.ajax({
      url : appRoot + '/everit_ecm_component_graph/graph.json',
    }).done(
        function(data) {
          renderECMGraph(data);

          // Center the graph
          var initialScale = 1;
          var svgWidth = $('svg.ecm-graph').innerWidth();
          var graphWidth = g.graph().width;
          zoom.translate([ (svgWidth - graphWidth * initialScale) / 2, 20 ])
              .scale(initialScale).event(svg);
          svg.attr('height', g.graph().height * initialScale + 40);
        });
  });

  $(window).resize(function() {
    resizeEcmGraphSVG();
  });

  this.refresh = function() {
    $.ajax({
      url : appRoot + '/everit_ecm_component_graph/graph.json',
    }).done(function(data) {
      renderECMGraph(data);
    });
  }
}

var ecmGraph = new EcmGraph();