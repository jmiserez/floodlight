package net.floodlightcontroller.happensbefore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.base64.Base64;
import org.jboss.netty.util.CharsetUtil;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Based on tutorial from here: http://docs.projectfloodlight.org/display/floodlightcontroller/How+to+Write+a+Module
 */
public class HappensBefore implements IFloodlightModule, IOFMessageListener {
	
	protected IFloodlightProviderService floodlightProvider;
	protected static Logger log;
	
	public final static String HAPPENSBEFORE_MSG_IN = "happensbefore_msg_in";
	public final static String HAPPENSBEFORE_MSG_IN_SWITCH = "happensbefore_msg_in_switch";
	
	@Override
	public String getName() {
		return HappensBefore.class.getSimpleName();
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
		
		ChannelBuffer buf = ChannelBuffers.dynamicBuffer();
		msg.writeTo(buf);
		ChannelBuffer encoded = Base64.encode(buf);
		String b64_msg = encoded.toString(CharsetUtil.UTF_8).replace("\n", "");
		
		if (msg.getType().equals(OFType.PACKET_IN)){
			cntx.getStorage().put(HAPPENSBEFORE_MSG_IN, b64_msg);
			cntx.getStorage().put(HAPPENSBEFORE_MSG_IN_SWITCH, sw.getId());
			System.out.format("HappensBefore-MessageIn:%d:%s", sw.getId(), b64_msg);
		} else {
			String in_msg = (String) cntx.getStorage().get(HAPPENSBEFORE_MSG_IN);
			Long in_msg_switch_id = (Long) cntx.getStorage().get(HAPPENSBEFORE_MSG_IN_SWITCH);

			if(in_msg != null && in_msg_switch_id != null){
				System.out.format("HappensBefore-MessageOut:%d:%s:%d:%s", in_msg_switch_id, in_msg, sw.getId(), b64_msg);
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
	    log = LoggerFactory.getLogger(HappensBefore.class);
	    log.debug("HappensBefore logger ready");
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// in events
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		// out events
		floodlightProvider.addOFMessageListener(OFType.BARRIER_REQUEST, this);
		floodlightProvider.addOFMessageListener(OFType.FLOW_MOD, this);
		floodlightProvider.addOFMessageListener(OFType.PACKET_OUT, this);
	}

}