package net.floodlightcontroller.happensbefore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Based on tutorial from here: http://docs.projectfloodlight.org/display/floodlightcontroller/How+to+Write+a+Module
 * 
 * Very simple, lots of redundant code. Should be refactored.
 * 
 */
public class FlawedFirewall implements IFloodlightModule, IOFMessageListener {
	
	protected IFloodlightProviderService floodlightProvider;
	protected static Logger log;
	protected HashMap<Long, HashMap<Long, Short>> switchMacPortMappings; // Switch -> (MAC -> Port)
	
	protected HashSet<Integer> outgoingConnections; // [IP addresses]
	protected int firewalledHost;
	
	//which host is the "internal" host?
	public final static int FIREWALLED_HOST = IPv4.toIPv4Address("10.0.0.1");
	public final static int FIREWALLED_HOST_STS = IPv4.toIPv4Address("123.123.1.1");

	protected final static boolean ENABLE_FIREWALL = true; //setting this to false also disables flows
	protected final static boolean USE_STS = true;
	protected final static boolean USE_FLOWS = true; //TODO JM: set true
	protected final static boolean USE_CONTROLLER_STATE = false; //TODO JM: set false // only store state as rules inside the switches
	protected final static boolean INDUCE_RACE_CONDITION = false;
	protected final static boolean ONLY_DROP_INBOUND_IF_NOT_UNSOLICITED = false; //TODO JM: what does this do again?
	protected final static short IDLE_TIMEOUT = 0;
	protected final static short HARD_TIMEOUT = 0;
	
	@Override
	public String getName() {
		return FlawedFirewall.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		OFPacketIn pi = (OFPacketIn) msg;

//		log.info(IPv4.fromIPv4Address(((IPv4)eth.getPayload()).getSourceAddress()));
//		log.info(IPv4.fromIPv4Address(((IPv4)eth.getPayload()).getDestinationAddress()));

		Long switchId = sw.getId();
		Short inPort = pi.getInPort();
		Long srcMacHash = Ethernet.toLong(eth.getSourceMACAddress());
		Long dstMacHash = Ethernet.toLong(eth.getDestinationMACAddress());
		
        if ((dstMacHash & 0xfffffffffff0L) == 0x0180c2000000L) { // from LearningSwitch
        	log.debug("For switch {}: packet addressed to 802.1D/Q reserved addr {}",
        			switchId,
        			HexString.toHexString(dstMacHash));
            return Command.STOP;
        }
		
        log.info("For switch {}: handling packet on port {} from {} for {}",
    			new Object[]{
    			switchId,
    			inPort,
    			HexString.toHexString(srcMacHash),
    			HexString.toHexString(dstMacHash),
    			});
		
                
		HashMap<Long, Short> macPortMapping = switchMacPortMappings.get(switchId);
		if(macPortMapping == null){
			macPortMapping = new HashMap<Long, Short>();
			switchMacPortMappings.put(switchId, macPortMapping);
		}
		
		if ((srcMacHash & 0x010000000000L) == 0) { // from LearningSwitch
            // Source MAC is not a broadcast address
			if (!macPortMapping.containsKey(srcMacHash)) {
	        	log.info("For switch {}: seen {} for the first time, is on port {}",
	        			new Object[]{
	        			switchId,
	        			HexString.toHexString(srcMacHash),
	        			inPort
	        			});
	        } else {
	        	if (macPortMapping.get(srcMacHash) != inPort){
	        		log.info("For switch {}: {} changed port from {} to {}",
	            			new Object[]{
	            			HexString.toHexString(srcMacHash),
	            			switchId,
	            			macPortMapping.get(srcMacHash),
	            			inPort
	        				});
	        	}
	        }
	        
	        log.debug("Adding mapping for switch {}: MAC {} -> Port {}",
	    			new Object[]{
	    			switchId,
	    			HexString.toHexString(srcMacHash),
	    			inPort
	    			});
			
			//update mapping
	        macPortMapping.put(srcMacHash, inPort);
        }
		
        //software switch logic
        OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        po.setBufferId(pi.getBufferId()).setInPort(pi.getInPort());

		//default is to flood
    	Short outPort = OFPort.OFPP_FLOOD.getValue();

        if ((dstMacHash & 0x010000000000L) == 0) { // from LearningSwitch
            // Destination MAC is not a broadcast address
        
        	if(macPortMapping.containsKey(dstMacHash)){
        		outPort = macPortMapping.get(dstMacHash);
        		log.info("For switch {}: Successful port lookup for destination MAC {}",
        				switchId,
        				HexString.toHexString(dstMacHash));
        	} else {
        		log.info("For switch {}: Failed port lookup for destination MAC {}",
        				switchId,
        				HexString.toHexString(dstMacHash));
        	}
        }
        
        //firewall logic
        
		Integer srcIp = null;
		Integer dstIp = null;
		
		if (eth.getPayload() instanceof IPv4){
			srcIp = ((IPv4)eth.getPayload()).getSourceAddress();
			dstIp = ((IPv4)eth.getPayload()).getDestinationAddress();
		}
        
        boolean trafficAllowed = false;
        boolean setupReverseFlow = false;

        if (FlawedFirewall.ENABLE_FIREWALL && srcIp != null && srcIp == firewalledHost){
        	//this device is allowed to establish connections
        	//add or update state
        	if(FlawedFirewall.USE_CONTROLLER_STATE || FlawedFirewall.ONLY_DROP_INBOUND_IF_NOT_UNSOLICITED){
        		outgoingConnections.add(dstIp);
        	}
        	trafficAllowed = true;
        	setupReverseFlow = true;
        	log.info("Firewall on {}: ALLOW Outgoing traffic from {} to {}",
        			new Object[]{
    				switchId,
    				srcIp != null ? IPv4.fromIPv4Address(srcIp) : "null",
    			    dstIp != null ? IPv4.fromIPv4Address(dstIp) : "null"
    				});
        } else if (FlawedFirewall.ENABLE_FIREWALL && dstIp != null && dstIp == firewalledHost) {
    		//an external device wants to communicate with the firewalled device
        	//and there was no appropriate flow in place (!)
        	if(FlawedFirewall.USE_CONTROLLER_STATE && outgoingConnections.contains(srcIp)){
        		trafficAllowed = true;
        		log.info("Firewall on {}: ALLOW Incoming traffic from {} to {}",
            			new Object[]{
        				switchId,
        				srcIp != null ? IPv4.fromIPv4Address(srcIp) : "null",
        	    			    dstIp != null ? IPv4.fromIPv4Address(dstIp) : "null"
        				});
        	} else if (FlawedFirewall.ONLY_DROP_INBOUND_IF_NOT_UNSOLICITED && !outgoingConnections.contains(srcIp)){ //special case for STS
        		trafficAllowed = true;
        		log.info("Firewall on {}: ALLOW Incoming traffic from {} to {}",
            			new Object[]{
        				switchId,
        				srcIp != null ? IPv4.fromIPv4Address(srcIp) : "null",
        	    			    dstIp != null ? IPv4.fromIPv4Address(dstIp) : "null"
        				});
        	} else {
	        	log.info("Firewall on {}: DENY  Incoming traffic from {} to {}",
	        			new Object[]{
	    				switchId,
	    				srcIp != null ? IPv4.fromIPv4Address(srcIp) : "null",
	    	    			    dstIp != null ? IPv4.fromIPv4Address(dstIp) : "null"
	    				});
        	}
        } else {
        	//we allow all other connections
        	log.info("Firewall on {}: IGNORE traffic from {} to {}",
        			new Object[]{
    				switchId,
    				srcIp != null ? IPv4.fromIPv4Address(srcIp) : "null",
    				dstIp != null ? IPv4.fromIPv4Address(dstIp) : "null"
    				});
        	trafficAllowed = true;
        }
        
        if(trafficAllowed){
	        // set actions
	        OFActionOutput action = new OFActionOutput().setPort(outPort);
	        po.setActions(Collections.singletonList((OFAction)action));
	        po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
	
	        // set data if is is included in the packetin
	        if (pi.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
	        	byte[] packetData = pi.getPacketData();
	        	po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
	        			+ po.getActionsLength() + packetData.length));
	        	po.setPacketData(packetData);
	        } else {
	        	po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
	        			+ po.getActionsLength()));
	        }
	        
	        //NOTE: Removing this will have the effect of all packets being dropped when there is no flow installed yet.
	        try {
	        	sw.write(po, cntx);
	        } catch (IOException e) {
	        	log.error("Failure writing PacketOut", e);
	        }
        
	        if(FlawedFirewall.ENABLE_FIREWALL && FlawedFirewall.USE_FLOWS){
	        	if(FlawedFirewall.INDUCE_RACE_CONDITION){
		        	try {
		        		log.info("Inducing race condition: Packet forwarded, now waiting 10s before installing OF rules");
						Thread.sleep(10000);
		        		log.info("Inducing race condition: Installing OF rules now");
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
	        	}
		        //now install flow in first direction
		        
		        OFMatch match = new OFMatch();
		        match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		        
		        //we want a flow that matches:
		        // - Switch input port OFPFW_IN_PORT
		        // - Source IP address OFPFW_DL_SRC
		        // - Destination IP address OFPFW_DL_DST
		        // - all bits of the source address: OFPFW_NW_SRC_MASK
		        // - all bits of the destination address: OFPFW_NW_DST_MASK
		        
		        match.setWildcards(((Integer)sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).intValue()
		                & ~OFMatch.OFPFW_IN_PORT
//		                & ~OFMatch.OFPFW_DL_SRC & ~OFMatch.OFPFW_DL_DST
		                & ~OFMatch.OFPFW_NW_SRC_MASK & ~OFMatch.OFPFW_NW_DST_MASK);
		        
		        short command = OFFlowMod.OFPFC_ADD;
		        long cookie = 0;
		        short priority = 20;
		        int bufferId = OFPacketOut.BUFFER_ID_NONE;
		
		        OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		        flowMod.setMatch(match);
		        flowMod.setCookie(cookie);
		        flowMod.setCommand(command);
		        flowMod.setIdleTimeout(FlawedFirewall.IDLE_TIMEOUT);
		        flowMod.setHardTimeout(FlawedFirewall.HARD_TIMEOUT);
		        flowMod.setPriority(priority);
		        flowMod.setBufferId(bufferId);
		        flowMod.setOutPort((command == OFFlowMod.OFPFC_DELETE) ? outPort : OFPort.OFPP_NONE.getValue());
		        flowMod.setFlags((command == OFFlowMod.OFPFC_DELETE) ? 0 : (short) (1 << 0)); // OFPFF_SEND_FLOW_REM
		
		        flowMod.setActions(Arrays.asList((OFAction) new OFActionOutput(outPort, (short) 0xffff)));
		        flowMod.setLength((short) (OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH));
		
		        log.info("{} {} flow mod {}",
		                      new Object[]{ sw, (command == OFFlowMod.OFPFC_DELETE) ? "deleting" : "adding", flowMod });
		
		        // and write it out
		        try {
		            sw.write(flowMod, null);
		        } catch (IOException e) {
		            log.error("Failed to write {} to switch {}", new Object[]{ flowMod, sw }, e);
		        }
		        
		        if(setupReverseFlow){
			        // now install the appropriate inverse flow
			        
		        	OFMatch reverseMatch = match.clone();
		        	reverseMatch.setDataLayerSource(match.getDataLayerDestination())
			          .setDataLayerDestination(match.getDataLayerSource())
			          .setNetworkSource(match.getNetworkDestination())
			          .setNetworkDestination(match.getNetworkSource())
			          .setTransportSource(match.getTransportDestination())
			          .setTransportDestination(match.getTransportSource())
			          .setInputPort(outPort);
			        
			        outPort = inPort;
			        
			        flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
			        flowMod.setMatch(reverseMatch);
			        flowMod.setCookie(cookie);
			        flowMod.setCommand(command);
			        flowMod.setIdleTimeout(FlawedFirewall.IDLE_TIMEOUT);
			        flowMod.setHardTimeout(FlawedFirewall.HARD_TIMEOUT);
			        flowMod.setPriority(priority);
			        flowMod.setBufferId(bufferId);
			        flowMod.setOutPort((command == OFFlowMod.OFPFC_DELETE) ? outPort : OFPort.OFPP_NONE.getValue());
			        flowMod.setFlags((command == OFFlowMod.OFPFC_DELETE) ? 0 : (short) (1 << 0)); // OFPFF_SEND_FLOW_REM
			
			        flowMod.setActions(Arrays.asList((OFAction) new OFActionOutput(outPort, (short) 0xffff)));
			        flowMod.setLength((short) (OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH));
			
			        log.info("{} {} flow mod {}",
			                      new Object[]{ sw, (command == OFFlowMod.OFPFC_DELETE) ? "deleting" : "adding", flowMod });
			
			        // and write it out
			        try {
			            sw.write(flowMod, null);
			        } catch (IOException e) {
			            log.error("Failed to write {} to switch {}", new Object[]{ flowMod, sw }, e);
			        }
		        }
	        }
        } else {
        	if(FlawedFirewall.ENABLE_FIREWALL && FlawedFirewall.USE_FLOWS){
        		OFMatch match = new OFMatch();
		        match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		        
		        match.setWildcards(((Integer)sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).intValue()
		                & ~OFMatch.OFPFW_IN_PORT
//		                & ~OFMatch.OFPFW_DL_SRC & ~OFMatch.OFPFW_DL_DST
		                & ~OFMatch.OFPFW_NW_SRC_MASK & ~OFMatch.OFPFW_NW_DST_MASK);
		        
		        short command = OFFlowMod.OFPFC_ADD;
		        long cookie = 0;
		        short priority = 15;
		        int bufferId = OFPacketOut.BUFFER_ID_NONE;
		
		        OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		        flowMod.setMatch(match);
		        flowMod.setCookie(cookie);
		        flowMod.setCommand(command);
		        flowMod.setIdleTimeout(FlawedFirewall.IDLE_TIMEOUT);
		        flowMod.setHardTimeout(FlawedFirewall.HARD_TIMEOUT);
		        flowMod.setPriority(priority);
		        flowMod.setBufferId(bufferId);
		        flowMod.setOutPort((command == OFFlowMod.OFPFC_DELETE) ? outPort : OFPort.OFPP_NONE.getValue());
		        flowMod.setFlags((command == OFFlowMod.OFPFC_DELETE) ? 0 : (short) (1 << 0)); // OFPFF_SEND_FLOW_REM
		
		        List<OFAction> actions = new LinkedList<OFAction>();
		        int actionsLength = 0;
		        flowMod.setActions(actions);
		        flowMod.setLength((short) (OFFlowMod.MINIMUM_LENGTH + actionsLength));
		
		        log.info("{} {} flow mod {}",
		                      new Object[]{ sw, (command == OFFlowMod.OFPFC_DELETE) ? "deleting" : "adding", flowMod });
		
		        // and write it out
		        try {
		            sw.write(flowMod, null);
		        } catch (IOException e) {
		            log.error("Failed to write {} to switch {}", new Object[]{ flowMod, sw }, e);
		        }
        	}
        }
        
        return Command.CONTINUE;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
	    log = LoggerFactory.getLogger(FlawedFirewall.class);
	    log.debug("FlawedFirewall logger ready");
	    switchMacPortMappings = new HashMap<Long, HashMap<Long,Short>>();
	    outgoingConnections = new HashSet<Integer>();
	    if(FlawedFirewall.USE_STS){
	    	firewalledHost = FIREWALLED_HOST_STS;
	    } else {
	    	firewalledHost = FIREWALLED_HOST;
	    }
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}

}