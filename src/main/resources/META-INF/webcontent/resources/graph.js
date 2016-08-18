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
function Node(nodeId) {
  this.nodeId=nodeId;
  this.parents={};
  this.children={};

}
Node.prototype.addChildren= function(node){
  this.children[node.nodeId]=node;
}

Node.prototype.removeChildren= function(node){
  if (node.nodeId in this.children)
  { 
      delete this.children[node.nodeId];
  }
}

Node.prototype.addParent= function(node){
  this.parents[node.nodeId]=node;
}

Node.prototype.removeParent= function(node){
  if (node.nodeId in this.parents)
  { 
      delete this.parents[node.nodeId];
  }
}

function Graph(){
  this.nodes={};
}
Graph.prototype.addNewNode= function(nodeId){
  if (!(nodeId in this.nodes)){ 
      this.nodes[nodeId]=new Node(nodeId);
  }
}

Graph.prototype.addNodeConnection= function(parentNodeId, childrenNodeId){
  this.addNewNode(parentNodeId);
  this.addNewNode(childrenNodeId);
  this.nodes[parentNodeId].addChildren(this.nodes[childrenNodeId]);
  this.nodes[childrenNodeId].addParent(this.nodes[parentNodeId]);
}

Graph.prototype.getParentsPath= function(nodeId){
   var result={};
   var parents=this.nodes[nodeId].parents;
   this.getParentsPathReq(parents,result);
   return result;
}
Graph.prototype.getParentsPathReq =function (parents,result){
     for (var attrname in parents) 
	 { result[attrname] = parents[attrname]; 
	   this.getParentsPathReq( parents[attrname].parents ,result);
	 }
}
Graph.prototype.getChildrenPath= function(nodeId){
   var result={};
   var children=this.nodes[nodeId].children;
   this.getChildrenPathReq(children,result);
   return result;
}
Graph.prototype.getChildrenPathReq =function (children,result){
     for (var attrname in children) 
	 { result[attrname] = children[attrname]; 
	   this.getChildrenPathReq( children[attrname].children, result);
	 }
}
Graph.prototype.getClosestNeighbours= function(nodeId){
	var children=this.nodes[nodeId].children;
	var parents=this.nodes[nodeId].parents;
	var nearNodes=[];
	var nearEdges=[];
	for (var attrname in children) {
		nearNodes.push( children[attrname].nodeId);
		nearEdges.push( {parent : nodeId, child :  children[attrname].nodeId});
	}
	for (var attrname in parents) {
		nearNodes.push( parents[attrname].nodeId);
		nearEdges.push( {parent : parents[attrname].nodeId, child :  nodeId});
	}
	var result ={nearNodes: nearNodes,nearEdges: nearEdges };
	return result;
}
Graph.prototype.getBloodPath =function (nodeId){
    var childrenResult=this.getChildrenPath(nodeId);
	var parentsPathResult=this.getParentsPath(nodeId);
	var result={};
    for (var attrname in childrenResult){
		result[attrname] = childrenResult[attrname]; 	   
	}
	for (var attrname in parentsPathResult){
		result[attrname] = parentsPathResult[attrname]; 	   
	}
    return result;
}
Graph.prototype.getBloodPathEdges =function (nodeId){
   var result= [ ]; 
   var accessedNodes= {};
   this.getChildrenPathEdgesReq(nodeId, accessedNodes,result);
    delete accessedNodes[nodeId];
   this.getParentsPathEdgesReq(nodeId, accessedNodes,result);
   return result;
}

Graph.prototype.getChildrenPathEdgesReq =function (nodeId, accessedNodes, result){
if (!(nodeId in accessedNodes)){ 
   accessedNodes[nodeId]=nodeId;
	 var children=this.nodes[nodeId].children;
	   for (var attrname in children) 
	   { 
		 result.push( {parent : nodeId, child : attrname});
		 this.getChildrenPathEdgesReq(attrname,accessedNodes,result);
	   }
	}   
}
Graph.prototype.getParentsPathEdgesReq =function (nodeId,accessedNodes,result){
  if (!(nodeId in accessedNodes)){ 
     accessedNodes[nodeId]=nodeId;
	   var parents=this.nodes[nodeId].parents;
		for (var attrname in parents) 
	   { 
		 result.push( {parent : attrname, child : nodeId});
		 this.getParentsPathEdgesReq(attrname,accessedNodes,result);
	   }
   }
}