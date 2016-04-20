function ecmGraph() {
  // Create the input graph
  var g = new dagreD3.graphlib.Graph().setGraph({});
  var appRoot = $('div.ecm-graph').attr('data-ecm-appRoot');

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
      return $('<p>Bundle Capability</p>')[0];
    }
  }

  var resolveTooltip = function(node) {
    if (node.capability) {
      var result = '<ul style="text-align: left;">';
      var attributes = node.capability.attributes;
      for ( var key in attributes) {
        if (attributes.hasOwnProperty(key)) {
          result = result + '<li><strong style="font-weight: bold;">' + key
              + ':</strong> ' + attributes[key] + '</li>';
        }
      }
      result = result + '</ul>';
      return result;
    } else {
      return "<p>TODO</p>";
    }
  }

  var addCapabilityNodeWithEdge = function(capability) {
    var additionalClasses = '';
    if (capability.componentState) {
      additionalClasses = ' componentstate-' + capability.componentState;
    } else {
      additionalClasses = ' componentstate-ACTIVE';
    }
    g.setNode(capability.nodeId, {
      label : resolveCapabilityLabel(capability),
      shape : 'circle',
      rx : 5,
      ry : 5,
      capability : capability,
      class : 'capability' + additionalClasses
    });
    if (capability.componentNodeId) {
      g.setEdge(capability.nodeId, capability.componentNodeId, {
        arrowhead : 'undirected',
        arrowheadClass : 'arrowhead',
        class : 'capability-line'
      });
    }
  }

  var applyEcmGraphOnDagreG = function(ecmGraph) {
    var components = ecmGraph.components;

    for (i = 0; i < components.length; i++) {
      var component = components[i];
      var additionalClasses = ' componentstate-' + component.state;

      g.setNode(component.nodeId, {
        label : component.name,
        rx : 5,
        ry : 5,
        class : 'component' + additionalClasses
      });

      var requirements = component.requirements;
      for (j = 0; j < requirements.length; j++) {
        var requirement = requirements[j];

        if (requirement.capabilityNodeId) {
          g.setEdge(component.nodeId, requirement.capabilityNodeId, {
            label : requirement.requirementId,
            arrowheadClass : 'arrowhead',
            class : 'requirement'
          });
        } else {
          // TODO
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

var ecmGraphObj = new ecmGraph();