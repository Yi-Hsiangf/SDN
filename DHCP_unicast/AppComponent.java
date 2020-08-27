/*
 * Copyright 2019-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package yi.app;

import com.google.common.collect.ImmutableSet;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.MacAddress;
import org.onlab.graph.Edge;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;

import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyVertex;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.Link;
import org.onosproject.net.topology.DefaultTopologyVertex;
import org.onosproject.net.topology.DefaultTopologyEdge;

import org.onlab.packet.TpPort;

//for MAC address table
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.LinkedHashSet;  
import java.util.List;
import java.util.ArrayList;
import java.util.*;

import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

	private static final int DEFAULT_TIMEOUT = 1000;
    private static final int DEFAULT_PRIORITY = 10;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;
	
	private int flowTimeout = DEFAULT_TIMEOUT;
	private int flowPriority = DEFAULT_PRIORITY;

    private final Logger log = LoggerFactory.getLogger(getClass());

    // T
    private final InternalConfigListener cfgListener = new InternalConfigListener();

    private final Set<ConfigFactory> factories = ImmutableSet.of(
            new ConfigFactory<ApplicationId, yi.app.MyConfig>(APP_SUBJECT_FACTORY,
                                                                yi.app.MyConfig.class,
                                                                "MyConfig") {
                @Override
                public yi.app.MyConfig createConfig() {
                    return new yi.app.MyConfig();
                }
            }
    );

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry cfgService;

    private static String myName = "defaultName";
    private ApplicationId appId;
	private ApplicationId config_appId;
	
	private DeviceId dhcp_connected_switch;
	private PortNumber dhcp_portnumber;
	
	public static TpPort port67 = TpPort.tpPort(67);
	public static TpPort port68 = TpPort.tpPort(68);
	
	
	private ReactivePacketProcessor processor = new ReactivePacketProcessor();

    @Activate
    protected void activate() {
        config_appId = coreService.registerApplication("yi.app.unicastdhcp");
        cfgService.addListener(cfgListener);
        factories.forEach(cfgService::registerConfigFactory);
        cfgListener.reconfigureNetwork(cfgService.getConfig(config_appId, yi.app.MyConfig.class));
		
		appId = coreService.registerApplication("yi.app");
		
		
		packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();
		

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        factories.forEach(cfgService::unregisterConfigFactory);
        log.info("Stopped");
    }

    private class InternalConfigListener implements NetworkConfigListener {

        /**
         * Reconfigures variable "myName" when there's new valid configuration uploaded.
         *
         * @param cfg configuration object
         */
        private void reconfigureNetwork(yi.app.MyConfig cfg) {
            if (cfg == null) {
                return;
            }
            if (cfg.myname() != null) {
                myName = cfg.myname();
            }
        }

        /**
         * To handle the config event(s).
         *
         * @param event config event
         */
        @Override
        public void event(NetworkConfigEvent event) {

            // While configuration is uploaded, update the variable "myName".
            if ((event.type() == NetworkConfigEvent.Type.CONFIG_ADDED ||
                    event.type() == NetworkConfigEvent.Type.CONFIG_UPDATED) &&
                    event.configClass().equals(yi.app.MyConfig.class)) {

                yi.app.MyConfig cfg = cfgService.getConfig(config_appId, yi.app.MyConfig.class);
                reconfigureNetwork(cfg);
                log.info("Reconfigured, unicast dhcp is {}", myName);
				
				String[] tokens = myName.split("/");
				
				log.info("Reconfigured, token0 is {}", tokens[0]);
				log.info("Reconfigured, token1 dhcp is {}", tokens[1]);
				
				dhcp_connected_switch = DeviceId.deviceId(tokens[0]);
				dhcp_portnumber = PortNumber.portNumber(tokens[1]);
				
				log.info("Reconfigured, unicast dhcp switch is  at {}", dhcp_connected_switch);
				log.info("Reconfigured, unicast dhcp is at port : {}", dhcp_portnumber);
				
            }
        }
    }
	
	//packet in
	private void requestIntercepts() {
        TrafficSelector.Builder selector_discover_request = DefaultTrafficSelector.builder();
		selector_discover_request.matchEthType(Ethernet.TYPE_IPV4)
				.matchIPProtocol(IPv4.PROTOCOL_UDP)
				.matchUdpSrc(port68);
		
		/*
        TrafficSelector.Builder selector_offer_ack = DefaultTrafficSelector.builder();
		selector_offer_ack.matchEthType(Ethernet.TYPE_IPV4)
				.matchIPProtocol(IPv4.PROTOCOL_UDP)
				.matchUdpSrc(port67);		
		*/
		//Requests that packets matching the given selector are punted from the dataplane
		//to the controller.
        packetService.requestPackets(selector_discover_request.build(), PacketPriority.REACTIVE, appId);
		//packetService.requestPackets(selector_offer_ack.build(), PacketPriority.REACTIVE, appId);
    }
	
	//packet in 的處理
	private class ReactivePacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            MacAddress src_macAddress = ethPkt.getSourceMAC();
			MacAddress dst_macAddress = ethPkt.getDestinationMAC();
			
            HostId dst_id = HostId.hostId(ethPkt.getDestinationMAC());
			HostId src_id = HostId.hostId(ethPkt.getSourceMAC());
			 

			
			if(ethPkt.getEtherType() == Ethernet.TYPE_ARP)
				return;
			
			if (isControlPacket(ethPkt)) {
                return;
            }
            // Do not process LLDP MAC address in any way.
            if (dst_id.mac().isLldp()) {
                return;
            }
			
			if(dst_id == null)
				return;		

			if(src_id == null)
				return;		
			
			Host src = hostService.getHost(src_id);
			PortNumber port_between_src_switch = src.location().port();
			
			DeviceId packet_in_device_id = pkt.receivedFrom().deviceId();
			
			
			TopologyGraph Graph = topologyService.getGraph(topologyService.currentTopology());
			Set<TopologyVertex> All_Vertex = Graph.getVertexes();
			
			log.info("packet in: {}", packet_in_device_id);
			log.info("src_macAddress: {}", src_macAddress);
			log.info("dst_macAddress: {}", dst_macAddress);
			//log.info("port_between_dst_switch: {}", port_between_dst_switch);
			//log.info("id: {}" , id);
			//log.info("dst: {}" , dst);
			
			
			
			// Are we on an edge switch that our destination is on? If so,
            // simply forward out to the destination and bail.
            if (pkt.receivedFrom().deviceId().equals(dhcp_connected_switch)) {
                if (!context.inPacket().receivedFrom().port().equals(dhcp_portnumber)) {
                    installRule(context, dhcp_portnumber, dhcp_connected_switch, 1);
					installRule(context, port_between_src_switch, dhcp_connected_switch, 2);
                }
                return;
            }
			
		
			Map<DeviceId, Map<DeviceId, PortNumber>> Map_device_output = new HashMap<DeviceId, Map<DeviceId, PortNumber>>();
			
			Map<DeviceId, Map<DeviceId, PortNumber>> reverse_device_output = new HashMap<DeviceId, Map<DeviceId, PortNumber>>();
			//adjacent_list
			Map<DeviceId, List<DeviceId>> adjVertices = new HashMap<DeviceId, List<DeviceId>>();
			for(TopologyVertex src_vertex : All_Vertex)
			{
				//log.info("vertex: {}", src_vertex);
				
				adjVertices.putIfAbsent(src_vertex.deviceId(), new ArrayList<>());
				
				Set<TopologyEdge> src_vertex_edge = Graph.getEdgesFrom(src_vertex);
				//log.info("Edge: {}", src_vertex_edge);
				
				Map<DeviceId, PortNumber> Map_port_to_dst = new HashMap<DeviceId, PortNumber>();
			
				for(TopologyEdge edge : src_vertex_edge)
				{
					DeviceId dst_vertex= edge.dst().deviceId();
					adjVertices.get(src_vertex.deviceId()).add(dst_vertex);
					
					//get output port
					PortNumber src_output_port = edge.link().src().port();
					Map_port_to_dst.put(dst_vertex, src_output_port);
					
				}
				Map_device_output.put(src_vertex.deviceId(),Map_port_to_dst);
			}
			
			log.info("Map_device_output: {}", Map_device_output);		
			
			//BFS
			Set<DeviceId> visited = new LinkedHashSet<DeviceId>();
			Queue<DeviceId> queue = new LinkedList<DeviceId>();
				
			Map<DeviceId, DeviceId> predecessor = new HashMap<DeviceId, DeviceId>();

			
			//log.info("adjVertices: {}", adjVertices);
			
			//root
			queue.add(packet_in_device_id);
			visited.add(packet_in_device_id);
			predecessor.put(packet_in_device_id,null);
			while(!queue.isEmpty())
			{
				DeviceId sw = queue.poll();
				for(DeviceId vertex : adjVertices.get(sw))
				{
					if(!visited.contains(vertex))
					{
						visited.add(vertex);
						queue.add(vertex);
						predecessor.put(vertex,sw);
					}
				}
			}
			//log.info("predecessor: {}", predecessor);
			//log.info("dst_nearst_switch: {}", dst_nearst_switch);
			if(predecessor.containsKey(dhcp_connected_switch))
			{	
				//log.info("install flow rule on : {}", dst_nearst_switch);
				//log.info("port_between_dst_switch: {}", port_between_dst_switch);
				installRule(context, dhcp_portnumber, dhcp_connected_switch, 1);
				
				
				DeviceId pre_vertex = predecessor.get(dhcp_connected_switch);
				DeviceId pro_vertex = dhcp_connected_switch;
				
				Map<DeviceId, PortNumber> map = Map_device_output.get(pro_vertex);
				PortNumber out_port = map.get(pre_vertex);		
				
				installRule(context, out_port, dhcp_connected_switch, 2);
				
				while(pre_vertex != null)
				{
					if(pre_vertex == packet_in_device_id)
					{
						map = Map_device_output.get(pre_vertex);
						out_port = map.get(pro_vertex);
						
						installRule(context, out_port, pre_vertex, 1); // to server
						installRule(context, port_between_src_switch, pre_vertex, 2); // to client
						break;
					}
					else
					{	
						map = Map_device_output.get(pre_vertex);
						out_port = map.get(pro_vertex);
					
						installRule(context, out_port, pre_vertex, 1);
						
						pro_vertex = pre_vertex;
						pre_vertex = predecessor.get(pre_vertex);					
					
						map = Map_device_output.get(pro_vertex);
						out_port = map.get(pre_vertex);
						installRule(context, out_port, pro_vertex, 2);			
					}
				}
			}
			
			//log.info("finish installed!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
		

			//log.info("vertex: {}", Vertex);
			//log.info("Edge: {}", Edge);
			//log.info("Edge_src: {}", Edge_src);
			
        }

    }
	// Indicates whether this is a control packet, e.g. LLDP, BDDP
    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }
	
	    // Install a rule forwarding the packet to the specified port.
    private void installRule(PacketContext context, PortNumber portNumber, DeviceId vertex, int flag) {
		
        Ethernet inPkt = context.inPacket().parsed();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();

		
		if(flag == 1)  //to server
		{
			selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
				.matchIPProtocol(IPv4.PROTOCOL_UDP)
				.matchUdpSrc(port68);
		
			TrafficTreatment treatment = DefaultTrafficTreatment.builder()
					.setOutput(portNumber)
					.build();
					
			//flow mod
			ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
					.withSelector(selectorBuilder.build())
					.withTreatment(treatment)
					.withPriority(flowPriority)
					.withFlag(ForwardingObjective.Flag.VERSATILE)
					.fromApp(appId)
					.makeTemporary(flowTimeout)
					.add();
					
			flowObjectiveService.forward(vertex,forwardingObjective);
			log.info("flag : {}", flag);
			log.info("install flow rule on : {}", vertex);
			log.info("out_port : {}", portNumber);
		}
		else if(flag == 2) // to client
		{
			selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
				.matchIPProtocol(IPv4.PROTOCOL_UDP)
				.matchUdpSrc(port67);
		
			TrafficTreatment treatment = DefaultTrafficTreatment.builder()
					.setOutput(portNumber)
					.build();
					
			//flow mod
			ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
					.withSelector(selectorBuilder.build())
					.withTreatment(treatment)
					.withPriority(flowPriority)
					.withFlag(ForwardingObjective.Flag.VERSATILE)
					.fromApp(appId)
					.makeTemporary(flowTimeout)
					.add();
					
			flowObjectiveService.forward(vertex,forwardingObjective);
			log.info("flag : {}", flag);
			log.info("install flow rule on : {}", vertex);
			log.info("out_port : {}", portNumber);			
		}
    }
	
}
