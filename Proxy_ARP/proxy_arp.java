package yi.app;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;

import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.MacAddress;
import org.onlab.graph.Edge;
import org.onlab.packet.IpAddress;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.ARP;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.edge.EdgePortService;

import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyVertex;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.Link;
import org.onosproject.net.link.LinkService;
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

import java.lang.Object;
import java.nio.ByteBuffer;
//for MAC address table
import java.util.Map;
import java.util.HashMap;
/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

	private static final int DEFAULT_TIMEOUT = 10;
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
	
	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LinkService linkService;
	
	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected EdgePortService edgePortService;	

	
	private int flowTimeout = DEFAULT_TIMEOUT;
	private int flowPriority = DEFAULT_PRIORITY;
	
	private Map<MacAddress, Ip4Address> arp_table = new HashMap<MacAddress, Ip4Address>();

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
			
			if(ethPkt.getEtherType() != Ethernet.TYPE_ARP)
				return;		
			
			ARP arp = (ARP) ethPkt.getPayload();
			short op_code = arp.getOpCode();
			byte[] src_IP = arp.getSenderProtocolAddress();
			byte[] src_MAC = arp.getSenderHardwareAddress();
			byte[] dst_MAC = arp.getTargetHardwareAddress();
			
			MacAddress src_mac = MacAddress.valueOf(src_MAC);
			MacAddress dst_mac = MacAddress.valueOf(dst_MAC);
			Ip4Address src_ip = Ip4Address.valueOf(src_IP);
			
			arp_table.put(src_mac, src_ip);
			log.info("arp_table : {}", arp_table);

			//reply
			if(op_code == 2)
			{
				log.info("op_code : {}", op_code);
				
				HostId dst_id = HostId.hostId(ethPkt.getDestinationMAC());
				Host dst = hostService.getHost(dst_id);
				if(dst == null)
					return;
				
				PortNumber port_to_dst = dst.location().port();
				DeviceId dst_nearest_switch = dst.location().deviceId();
				
				log.info("dst_nearest_switch : {}", dst_nearest_switch);
				//packet out
				TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(port_to_dst).build();
				OutboundPacket packet_out = new DefaultOutboundPacket(dst_nearest_switch, treatment, ByteBuffer.wrap(ethPkt.serialize()));
				packetService.emit(packet_out);				
				
			}
			else if(op_code == 1)
			{
				//request
				log.info("op_code : {}", op_code);
				if(arp_table.containsKey(dst_mac))
				{
					log.info("get ip from arp_table");
					//create arp reply
					Ip4Address dst_ip = arp_table.get(dst_mac);
					Ethernet arp_reply = ARP.buildArpReply(dst_ip, dst_mac, ethPkt);

					DeviceId packet_in_device_id = pkt.receivedFrom().deviceId();					
					//packet out
					HostId src_id = HostId.hostId(ethPkt.getSourceMAC());
					Host src = hostService.getHost(src_id);
					PortNumber port_between_src_switch = src.location().port();
					
					TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(port_between_src_switch).build();
					OutboundPacket packet_out = new DefaultOutboundPacket(packet_in_device_id, treatment, ByteBuffer.wrap(arp_reply.serialize()));
					packetService.emit(packet_out);	
					
					log.info("dst_mac : {}", dst_mac);
					log.info("dst_ip : {}", dst_ip);
				}
				else
				{
					log.info("first time");
					
					Iterable<ConnectPoint> edge_points = edgePortService.getEdgePoints();
					log.info("edge_points : {}", edge_points);
					
					for(ConnectPoint edge_point : edge_points)
					{
						DeviceId edge_switch = edge_point.deviceId();
						PortNumber out_port = edge_point.port();
						
						//packet out
						TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(out_port).build();
						OutboundPacket packet_out = new DefaultOutboundPacket(edge_switch, treatment, ByteBuffer.wrap(ethPkt.serialize()));
						packetService.emit(packet_out);	
						log.info("edge_point : {}", edge_point);
					}				
					
				}
			}
        }

    }

	
}

