package yi.app;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.MacAddress;
import org.onlab.graph.Edge;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
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


    private ReactivePacketProcessor processor = new ReactivePacketProcessor();
	private ApplicationId appId;
	
    @Activate
    protected void activate() {
		appId = coreService.registerApplication("yi.app");
		
		packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();

		log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
    }
	
	//Request packet in via packet service.
	//判斷match field 
	//packet in IPv4 packet
    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
		//Requests that packets matching the given selector are punted from the dataplane
		//to the controller.
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }
	
	
	//packet in 的處理
	private class ReactivePacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            MacAddress src_macAddress = ethPkt.getSourceMAC();
			MacAddress dst_macAddress = ethPkt.getDestinationMAC();
            HostId id = HostId.hostId(ethPkt.getDestinationMAC());
			
			 
			 
			
			if(ethPkt.getEtherType() == Ethernet.TYPE_ARP)
				return;
			
			if (isControlPacket(ethPkt)) {
                return;
            }
            // Do not process LLDP MAC address in any way.
            if (id.mac().isLldp()) {
                return;
            }
			
			if(id == null)
				return;
			
			Host dst = hostService.getHost(id);

			if (dst == null) {
				return;
			}
			DeviceId dst_nearst_switch = dst.location().deviceId();
			PortNumber port_between_dst_switch = dst.location().port();
			
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
            if (pkt.receivedFrom().deviceId().equals(dst.location().deviceId())) {
                if (!context.inPacket().receivedFrom().port().equals(dst.location().port())) {
                    installRule(context, dst.location().port(), dst_nearst_switch);
                }
                return;
            }
			
			
			//adjacent_list
			Map<DeviceId, List<DeviceId>> adjVertices = new HashMap<DeviceId, List<DeviceId>>();
			Map<DeviceId, Map<DeviceId, PortNumber>> Map_device_output = new HashMap<DeviceId, Map<DeviceId, PortNumber>>();			
			
			for(TopologyVertex src_vertex : All_Vertex)
			{
				//log.info("vertex: {}", src_vertex);
				
				adjVertices.putIfAbsent(src_vertex.deviceId(), new ArrayList<>());
				
				Set<TopologyEdge> src_vertex_edge = Graph.getEdgesFrom(src_vertex);
				
				log.info("src_vertex: {}", src_vertex);
				log.info("src_vertex_edge: {}", src_vertex_edge);
				Map<DeviceId, PortNumber> Map_port_to_dst = new HashMap<DeviceId, PortNumber>();
				
				for(TopologyEdge edge : src_vertex_edge)
				{
					log.info("edge: {}", edge);
					DeviceId dst_vertex= edge.dst().deviceId();
					adjVertices.get(src_vertex.deviceId()).add(dst_vertex);
					
					//get output port
					PortNumber src_output_port = edge.link().src().port();
					
					log.info("dst_vertex: {}", dst_vertex);
					log.info("src_output_port: {}", src_output_port);
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
					log.info("sw: {}", sw);	
					log.info("vertex in sw: {}", vertex);
					if(!visited.contains(vertex))
					{
						visited.add(vertex);
						queue.add(vertex);
						predecessor.put(vertex,sw);
					}
				}
			}
			log.info("predecessor: {}", predecessor);
	
			//log.info("dst_nearst_switch: {}", dst_nearst_switch);
			if(predecessor.containsKey(dst_nearst_switch))
			{	
				//log.info("install flow rule on : {}", dst_nearst_switch);
				//log.info("port_between_dst_switch: {}", port_between_dst_switch);
				installRule(context, port_between_dst_switch, dst_nearst_switch);
				
				DeviceId pre_vertex = predecessor.get(dst_nearst_switch);
				DeviceId pro_vertex = dst_nearst_switch;
				while(pre_vertex != null)
				{
					
					Map<DeviceId, PortNumber> map = Map_device_output.get(pre_vertex);
					PortNumber out_port = map.get(pro_vertex);
					
					
					log.info("pre_vertex: {}", pre_vertex);
					log.info("pro_vertex: {}", pro_vertex);
					
					if(pre_vertex == packet_in_device_id)
					{
						installRule(context, out_port, pre_vertex);
						break;
					}
					else
					{			
						installRule(context, out_port, pre_vertex);
					
						pro_vertex = pre_vertex;
						pre_vertex = predecessor.get(pre_vertex);				
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
    private void installRule(PacketContext context, PortNumber portNumber, DeviceId vertex) {
		
        Ethernet inPkt = context.inPacket().parsed();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();


        selectorBuilder.matchEthDst(inPkt.getDestinationMAC());
	
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
		log.info("install flow rule on : {}", vertex);
		log.info("out_port : {}", portNumber);
    }
}
