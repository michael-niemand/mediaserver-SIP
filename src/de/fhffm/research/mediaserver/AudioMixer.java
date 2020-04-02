/*
 * class AudioMixer
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

//import javax.sip.header.CallIdHeader;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.ListIterator;

import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;

import org.gstreamer.Gst;

import org.gstreamer.Pad;
import org.gstreamer.PadLinkReturn;
import org.gstreamer.Pipeline;
import org.gstreamer.State;
import org.gstreamer.elements.good.RTPBin;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

public class AudioMixer extends MediaProcessor {

	private static final String PROPERTIES_FILE_NAME = "MediaServer.properties";

	private final static String CAPABILITY = "application/x-rtp, media=audio, payload=0, clock-rate=8000, encoding-name=PCMU";

	//private final static int MSECOND = 1000; 

	private int RECV_PORT;
	private String RECV_IP;
	private int maxParticipants;
	private int QUEUETIME = 50;

	private int counter = 0;
	//private ParticipantAudio[] participants;

	/****************************************************************************
	 * GST_Variables
	 ****************************************************************************/
	private Pipeline pipe;
	// elements for sending out the mixed data

	private RTPBin rtpbin;
	private Element udpsrc;
	//private Element[] udpsinks;
	
	@SuppressWarnings("unused")
	private PadLinkReturn linkOk;

	List<ParticipantAudio> participants = null;

	public void start(final List<ParticipantAudio> audioParticipants) throws ConfigurationException, UnknownHostException, SocketException{

		participants = audioParticipants;
		
		PropertiesConfiguration config = new PropertiesConfiguration();
		config.load(PROPERTIES_FILE_NAME);

		RECV_PORT=config.getInt("AUDIO_RECV_PORT");
		RECV_IP=config.getString("IP_ADDR");

		maxParticipants = config.getInt("MAX_PARTICIPANTS");

		// create the Pipeline
		pipe = new Pipeline("AudioMixer");
		//pipe.set("delay", MSECOND);

		// create the elements for the participants
		ListIterator<ParticipantAudio> it1 = audioParticipants.listIterator();
		
		while(it1.hasNext()){
			ParticipantAudio pa = it1.next();

			pa.setQueueSize(QUEUETIME);
			pa.init();

		}
	
		/***************************************************
		 * create the receiver for RTP
		 ****************************************************/

		udpsrc = ElementFactory.make("udpsrc", "udpsrc");
		udpsrc.set("uri", "udp://" + RECV_IP + ":" + RECV_PORT);
		udpsrc.set("caps", new Caps(CAPABILITY));

		rtpbin = (RTPBin) ElementFactory.make("gstrtpbin", "rtpbin");
		rtpbin.set("autoremove", true);

		pipe.addMany(rtpbin, udpsrc);

		// link the udpsrc with rtpbin
		Pad sinkpad = rtpbin.getRequestPad("recv_rtp_sink_0");
		//Pad sinkpadRtcp = rtpbin.getRequestPad("recv_rtcp_sink_0");

		Pad srcpad = udpsrc.getStaticPad("src");
		srcpad.link(sinkpad);

		/***************************************************
		 * create the sender for RTP
		 ****************************************************/		

		ListIterator<ParticipantAudio> it2 = audioParticipants.listIterator();
		while(it2.hasNext()){
			
			ParticipantAudio pa = it2.next();
			pipe.addMany(pa.getElements());
			pipe.addMany(pa.getQueues());
			}		
		//cycle throug participants and link the pads ...
		ListIterator<ParticipantAudio> it3 = audioParticipants.listIterator();
		int i3 = 0;
		while(it3.hasNext()){
			
			ParticipantAudio pa = it3.next();
			// in
			Element.linkMany(pa.getElements()[0],pa.getElements()[1],pa.getElements()[2]);
			// out
			Element.linkMany(pa.getElements()[4],pa.getElements()[3], pa.getElements()[5], pa.getElements()[6]);

			// link current participants payloader out to send rtp in
			pa.getElements()[6].getStaticPad("src").link(rtpbin.getRequestPad("send_rtp_sink_%d"));

			// RTPBins send RTP source to udpsink
			rtpbin.getStaticPad("send_rtp_src_"+i3).link(pa.getUdpsink().getStaticPad("sink"));

			// only the OTHER participants get this - not the current p!
			// link all participants queues to the mixer of current participant
			ListIterator<ParticipantAudio> it4 = audioParticipants.listIterator();
			int i4 = 0;
			while(it4.hasNext()){

				ParticipantAudio pax = it4.next();
				if(!(pa.equals(pax))){

					/*
					 * 	1 - current tee --> all current queues
					 */
					// good example for code that is a nightmare to read:
					if(i4 < audioParticipants.size()){
						linkOk = pax.getElements()[2].getRequestPad("src%d").link(pax.getQueues()[i3].getStaticPad("sink"));
						//System.out.println("LINK tee" + i4 + "-src --> queue" + i3 + "_" + i4 + " sink: " + linkOk);
					}

					/*
					 * 	2 - current queues --> other mixers
					 */
					linkOk = pa.getQueues()[i4].getStaticPad("src").link(pax.getElements()[4].getRequestPad("sink%d"));
					//System.out.println("LINK participant_" + i + " queue_" + j + "-src --> participant_" + j + " mixer-sink: " + linkOk);
				}
				i4++;
			}
			i3++;
		}

		//create DOT-File for debugging
		GstDebugUtils.gstDebugBinToDotFile(pipe, 5, "audiopipe_before_adding_participants");

		/***************************************************
		 * create the participant-removed-listener
		 ****************************************************/		
		rtpbin.connect(new Element.PAD_REMOVED() {
			@Override
			public void padRemoved(Element elememt, Pad pad) {
				System.out.println("Audiomixer: Pad " + pad.getName() + " autoremoved");
				GstDebugUtils.gstDebugBinToDotFile(pipe, 5, "audiopipe_after_removing_participant_" + counter);
			}
		}); 

		/***************************************************
		 * create the new-participant-listener
		 ****************************************************/
		rtpbin.connect(new Element.PAD_ADDED() {			
			@Override
			public void padAdded(Element element, Pad pad) { 

				System.out.println("----------------------------------------------");
				System.out.println("NEW audio PAD: " + pad.getName());
				// only if max number of participants not yet reached 
				Element[] elements;

				// get the first participant, that has data set, but is not connected yet
				// If the depacketizer (elements[0]) is linked, then the element is connected!
				
				ListIterator<ParticipantAudio> it = audioParticipants.listIterator();
				ParticipantAudio pa = null;
				ParticipantAudio p = null;

				// cycle through all participants
				while(it.hasNext()){
					pa = it.next();
					if(!(pa.getCallId()==null)){
						// if Call ID is set, but ...
						if(!(pa.getElements()[0].getStaticPad("sink").isLinked())){
							// ... if depacketizer is NOT linked yet, then THIS is the participant we want!
							p = pa;
						}
					}
				}
				// TODO: This needs to be addressed properly
				if(p==null){
					System.out.println("Something is terribly wrong here ... I'm out!! *sound of somebody running away*");
				}
				
				// set the socket for sending
				p.getUdpsink().set("clients", p.socket());

				// TODO: this throws an error if a participant to much gets added, even though it never gets executed because of the check if(counter > maxParticipants)
				// retrieve the elements of the current participant ...
				try{
					//elements = participants[counter].getElements();	 
					elements = p.getElements();	 
				}
				catch(ArrayIndexOutOfBoundsException e) {        		
					System.out.println("ERR max Number of participants reached (" + maxParticipants + ")");
					elements = null;
					counter = 3;
				}
				//TODO: check if the client that wants to connect is the client that sent the invite

				// when the new pad is a recv rtp src
				//*******************************************

				if (pad.getName().indexOf("recv_rtp_src")>-1){ 

					// new recv_rtp_src --> depay
					//... and link the newly added pad to the sink of the current participants depacketizer (elements[0])
					linkOk = pad.link(elements[0].getStaticPad("sink"));
					//System.out.println("LINK recv-rtp-src --> participant_" + counter + " depay-sink: " + linkOk);

					GstDebugUtils.gstDebugBinToDotFile(pipe,  5, "audiopipe_after_adding_participant_" + (counter));	

					counter++;	
					return;
				}

				// when the new pad is a send rtp sink (created manually when new client connects)
				//*******************************************

				if (pad.getName().indexOf("send_rtp_sink")>-1){
					linkOk = elements[6].getStaticPad("src").link(pad);
					//System.out.println("LINK participant_" + counter + " pay-src --> rtpbin-send-rtp-sink" + counter + " sink: " + linkOk);   
					return;
				}

				// when the new pad is a send rtp src
				//*******************************************

				if (pad.getName().indexOf("send_rtp_src")>-1){

					return;
				}	
			}

		});

		/***************************************************
		 * get the whole thing going
		 ****************************************************/

		pipe.setState(State.READY);         
		pipe.setState(State.PLAYING);
		System.out.println("Audiomixer PLAYING");
		
		//Send Event that ready for Focus to go on ...	
		MediaprocessingReadyEvent evt = new MediaprocessingReadyEvent(this);
		this.fireReadyEvent(evt);
		
		Gst.main();

		pipe.setState(State.NULL);
		pipe.stop();
		Gst.deinit();
	}

	/**
	 * remove the participant from the conference
	 * 	
	 * @return <b>true</b> if successful
	 */
	@Override
	public void removeParticipant(String callId) {
	// remove the participant with the given call-ID from the mixer

		Element[] elements;
		ParticipantAudio pa=null;

		// try to remove, if found
		try	{
			pa = getParticipantByCallId(callId);

			elements = pa.getElements();

			//element[0] = depayloader
			Pad depaySinkPad;
			depaySinkPad = elements[0].getStaticPad("sink");

			String ssrc = getSsrcFromPadName(depaySinkPad.getPeer().getName());

			Pad recvRtpSrcPad = null;
			List<Pad> rtpBinSrcPads = rtpbin.getSrcPads();
			// cycle through all rtpbins pads to find the one with the name with the right SSRC
			for (Pad p : rtpBinSrcPads) {
				System.out.println("Padname: " + p.getName());
				if (getSsrcFromPadName(p.getName()) == ssrc){
					recvRtpSrcPad = p;
					
				}
			}
			System.out.println("unlinking audio depay-source-pad ...");
			recvRtpSrcPad.unlink(depaySinkPad);
			recvRtpSrcPad.dispose();
			
			System.out.println("unlinking audio payloader-source-pad ...");
			elements[6].getStaticPad("src").unlink(elements[6].getStaticPad("src").getPeer());
			
			counter--;
			ListIterator<ParticipantAudio> it = participants.listIterator();
			while(it.hasNext()){
				ParticipantAudio pa1 = it.next();
				if (pa1.getCallId().equals(callId)){
					it.remove();
					System.out.println("removing ParticipantAudio from list...");
					break;
				}
			}
		} 
		catch (NullPointerException e){
			System.out.println("ParticipantAudio to remove not found!");
		}
	}

	private ParticipantAudio getParticipantByCallId(String callId) {
		System.out.println("passed Call ID: " + callId);
		ParticipantAudio p=null;
		System.out.println("cycling through ParticipantAudio's ...");
		
		ListIterator<ParticipantAudio> it = participants.listIterator();
		while(it.hasNext()){
			ParticipantAudio pa = it.next();
			System.out.println("current ParticipantAudio's Call-ID: " + pa.getCallId());
			// TODO Null pointer Exception sometimes!? What to do, when P is not found?
			if (pa.getCallId().equals(callId)){
				p = pa;
				break;
			}
		}
		return p;
	}



}


