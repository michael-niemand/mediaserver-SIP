/*
 * class Participant
 * 
 * Version 0.1
 *
 * Date 12.3.2012
 * 
 * Copyright notice
 * 
 * This code is Open Source 
 * 
 * Author: Michael Niemand
 * 
 * 
 * 
 */


package de.fhffm.research.mediaserver;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;

public class ParticipantAudio extends Participant {
	
	/*
	 * CODEC:
	 * 0 .... aLaw
	 * 1 .... µLaw
	 * 2 .... GSM
	 */
	private static final String PROPERTIES_FILE_NAME = "MediaServer.properties";
	
	private int CODEC = 1;	
	private int qMaxSizetime = 50;
	private int mixerLatency = 20;
	private int intParticipants;
	private final static int MSECOND = 1000; 
	// data set via SIP
	private String ssrc;
	// gstreamer elements for processing media
	private Element depacketizer;
	private Element decoder;
	private Element mixer;
	private Element leveler;
	private Element tee;
	private Element[] queues;
	private Element packetizer;
	private Element encoder;
	private Element udpsink;
		
	public Element getUdpsink() {
		return udpsink;
	}

	public void setUdpsink(Element udpsink) {
		this.udpsink = udpsink;
	}

	/*
	 * Creates an instance of participant.
	 */
	public ParticipantAudio(){
		init();
	}
	
	public void init(){
		long identifier = System.nanoTime();
		String id = String.valueOf(identifier).substring(6);
		
		
		/***************************************************
		* elements to distribute own audio -->
		****************************************************/	
		
		// Load data from the config file
		PropertiesConfiguration config = new PropertiesConfiguration();
		try {
			config.load(PROPERTIES_FILE_NAME);
		} catch (ConfigurationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		intParticipants = config.getInt("MAX_PARTICIPANTS");	
		
		depacketizer = ElementFactory.make("rtppcmudepay", "depacketizer" + id);
		decoder = ElementFactory.make("mulawdec", "decoder" + id);

		tee = ElementFactory.make("tee", "tee"+id);

		queues = new Element[intParticipants];
		
		for(int i=0; i < intParticipants; i++){
			queues[i] = ElementFactory.make("queue", "queue" + id + "_" + i);
			/*
			 * (0): no               - Not Leaky
             * (1): upstream         - Leaky on upstream (new buffers)
             * (2): downstream       - Leaky on downstream (old buffers)
			 */
			queues[i].set("leaky", 0);
			queues[i].set("max-size-time", qMaxSizetime * MSECOND); 
			
//			//link the queues sinks to the source pads of the tee
//			linkOk = tee.getRequestPad("src%d").link(queues[i].getStaticPad("sink"));
//			System.out.println("LINK tee-src --> queue" + i + " sink: " + linkOk);
		}		
		
		/***************************************************
		* --> elements to mix other participants audio
		****************************************************/	
		
		// create elements for sending out the data
		mixer = ElementFactory.make("liveadder", "mixer" + id);
		mixer.set("latency", mixerLatency);
		
		leveler = ElementFactory.make("level", "leveler"+id);
			
		encoder = ElementFactory.make("mulawenc", "encoder" + id);
		packetizer = ElementFactory.make("rtppcmupay", "packetizer" + id);
		
		udpsink = ElementFactory.make("udpsink", "udpsink_" + id);
		udpsink.set("sync", false);
		
        // this is for aLaw, µLaw
        if(CODEC == 0 || CODEC == 1){
              packetizer.set("min-ptime", "20000000");
              packetizer.set("max-ptime", "20000000");
              packetizer.set("timestamp-offset", "0");
              packetizer.set("seqnum-offset", "0");
        }

        // this is for gsm
        else if(CODEC == 2){
              packetizer.set("min-ptime", "40000000");
              packetizer.set("max-ptime", "40000000");
              packetizer.set("timestamp-offset", "0");
              packetizer.set("seqnum-offset", "0");
        }		
       
	}
	
	public Element[] getElements(){
		Element[] elements;
			elements = new Element[8];
			//in
			elements[0] = depacketizer;
			elements[1] = decoder;
			elements[2] = tee;
			//out
			elements[3] = leveler;
			elements[4] = mixer;
			elements[5] = encoder;
			elements[6] = packetizer;
			elements[7] = udpsink;
	
		return elements;
	}
	//
	public Element[] getQueues(){
		return queues;
	}
	
	public void setQueueSize(int qMaxSizetime){
		if(qMaxSizetime < 1){
			this.qMaxSizetime = 1;
		}else if(qMaxSizetime > 1000){
			this.qMaxSizetime = 1000;
		}else{
			this.qMaxSizetime = qMaxSizetime;
		}
	}
	
	public String getSsrc() {
		return ssrc;
	}

	public void setSsrc(String ssrc) {
		this.ssrc = ssrc;
	}
}
