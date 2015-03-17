package net.floodlightcontroller.happensbefore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.FloodlightContextStore;
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
	
	protected static ConcurrentHashMap<Long, String> threadToLatestMsgIn = new ConcurrentHashMap<Long, String>();
	protected static final String HAPPENSBEFORE_MSG_IN = "net.floodlightcontroller.happensbefore.HappensBefore.msgin";
	protected static final List<OFType> IN_TYPES = Arrays.asList(OFType.PACKET_IN, OFType.FLOW_REMOVED);
	protected static final List<OFType> OUT_TYPES = Arrays.asList(OFType.PACKET_OUT, OFType.FLOW_MOD, OFType.BARRIER_REQUEST);
	protected static final FloodlightContextStore<String> hbStore = new FloodlightContextStore<String>();

	protected String formatMsg(OFMessage msg, long swid) {
		ChannelBuffer buf = ChannelBuffers.dynamicBuffer();
		msg.writeTo(buf);
		ChannelBuffer encoded = Base64.encode(buf);
		String b64_msg = encoded.toString(CharsetUtil.UTF_8).replace("\n", "");
		return Long.toString(swid)+":"+b64_msg;
	}
	
	@Override
	public String getName() {
		return HappensBefore.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// "name" should be called BEFORE this
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// "name" should be called AFTER this
		return !name.equals(getName()) && (IN_TYPES.contains(type) ||name.contains("HappensBeforePost"));
	}
	
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		
		String currentMsgString = this.formatMsg(msg, sw.getId());
		
		if (IN_TYPES.contains(msg.getType())){
			hbStore.put(cntx, HAPPENSBEFORE_MSG_IN, currentMsgString);
			System.out.format("net.floodlightcontroller.happensbefore.HappensBefore-MessageIn-[%s]%n", currentMsgString);
			System.out.flush();
			
			Thread t = Thread.currentThread();
			long workerId = t.getId();
			threadToLatestMsgIn.put(workerId, currentMsgString);
			
		}
		if (OUT_TYPES.contains(msg.getType())) {
			String previousMsgString = hbStore.get(cntx, HAPPENSBEFORE_MSG_IN);
			if(previousMsgString == null){
				log.error("Floodlight context not passed by previous module. Will analyze call stack instead: "+msg.getType().toString());
				
				// Get information by other means. We know that Floodlight uses only 1 single thread per switch connection.
				// So in case a module does not pass a context, we will just use the latest message in, if there is a Controller.handleMessage in the stack trace
				
				Thread t = Thread.currentThread();
				long workerId = t.getId();
				boolean found = false;
				StackTraceElement[] stack = Thread.currentThread().getStackTrace();
				for( StackTraceElement e : stack){
					if (e.getClassName().equals("net.floodlightcontroller.core.internal.Controller") &&
							e.getMethodName().equals("handleMessage")){
						// This is a reactive message out.
						previousMsgString = threadToLatestMsgIn.get(workerId); //TODO JM: hack, refactor
						found = true;
						break;
					}
				}
				if (!found){
					// This must be a proactive message out. Clear the latest message in and proceed.
					threadToLatestMsgIn.remove(workerId);
				}
			}
			if(previousMsgString != null){
				System.out.format("net.floodlightcontroller.happensbefore.HappensBefore-MessageOut-[%s:%s]%n", previousMsgString, currentMsgString);
				System.out.flush();
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
	    log.debug("HappensBefore logger loading.");
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		for (OFType type : IN_TYPES) {
			floodlightProvider.addOFMessageListener(type, this);
		}
		for (OFType type : OUT_TYPES) {
			floodlightProvider.addOFMessageListener(type, this);
		}
	}

}