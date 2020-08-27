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

				
			// Bail if this is deemed to be a control packet.
			if (isControlPacket(ethPkt)) {
                return;
            }
            // Do not process LLDP MAC address in any way.
            if (id.mac().isLldp()) {
                return;
            }

			
			/*
            // Do we know who this is for? If not, flood and bail.
            Host dst = hostService.getHost(id);
			if (dst == null) {
                flood(context);
                return;
            }
			*/
			
			/*
			//如果回傳packet in 的 device 是 host 連接的switch 就下 flow rule
			if (pkt.receivedFrom().deviceId().equals(dst.location().deviceId())) {
                if (!context.inPacket().receivedFrom().port().equals(dst.location().port())) {
                    installRule(context, dst.location().port());
                }
                return;
            }
			*/
			
			// mac address learning
			DeviceId device_id = pkt.receivedFrom().deviceId();
            PortNumber in_port = context.inPacket().receivedFrom().port();
			PortNumber out_port;
		
			
			Map<MacAddress, PortNumber> mac_table = new HashMap<MacAddress, PortNumber>();
			Map<DeviceId, Map<MacAddress, PortNumber>> device_table = new HashMap<DeviceId, Map<MacAddress, PortNumber>>();
			
			
			//learn mac address to avoid FLOOD next time
			mac_table.put(src_macAddress, in_port);
			device_table.put(device_id, mac_table);
			
			Map<MacAddress, PortNumber> map = device_table.get(device_id);
			if(map.containsKey(dst_macAddress))
			{
				out_port = map.get(dst_macAddress);
				// install a flow to avoid packet_in next time
				installRule(context, out_port);
			}
			else
				flood(context);
			
        }

    }
	// Indicates whether this is a control packet, e.g. LLDP, BDDP
    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }
	
	// Floods the specified packet if permissible.
    private void flood(PacketContext context) {
        packetOut(context, PortNumber.FLOOD);
    }
	
	private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }
	    // Install a rule forwarding the packet to the specified port.
    private void installRule(PacketContext context, PortNumber portNumber) {
        //
        // We don't support (yet) buffer IDs in the Flow Service so
        // packet out first.
        //
        Ethernet inPkt = context.inPacket().parsed();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();

        // If PacketOutOnly or ARP packet than forward directly to output port
        if (inPkt.getEtherType() == Ethernet.TYPE_ARP) {
            packetOut(context, portNumber);
            return;
        }

        selectorBuilder.matchInPort(context.inPacket().receivedFrom().port())
                .matchEthSrc(inPkt.getSourceMAC())
                .matchEthDst(inPkt.getDestinationMAC());
	
        IPv4 ipv4Packet = (IPv4) inPkt.getPayload();
        byte ipv4Protocol = ipv4Packet.getProtocol();
        Ip4Prefix matchIp4SrcPrefix =
                Ip4Prefix.valueOf(ipv4Packet.getSourceAddress(),
                                  Ip4Prefix.MAX_MASK_LENGTH);
        Ip4Prefix matchIp4DstPrefix =
                Ip4Prefix.valueOf(ipv4Packet.getDestinationAddress(),
                                  Ip4Prefix.MAX_MASK_LENGTH);
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(matchIp4SrcPrefix)
                .matchIPDst(matchIp4DstPrefix);
     
       
		// packet out => action
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
		// install forwarding rule on specific device
        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(),
                                     forwardingObjective);

    }
}
