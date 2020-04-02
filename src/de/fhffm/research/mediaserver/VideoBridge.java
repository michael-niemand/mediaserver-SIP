package de.fhffm.research.mediaserver;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.Event;
import org.gstreamer.Gst;
import org.gstreamer.Pad;
import org.gstreamer.PadLinkReturn;
import org.gstreamer.Pipeline;
import org.gstreamer.State;
import org.gstreamer.elements.good.RTPBin;
import org.gstreamer.event.EOSEvent;

public class VideoBridge extends MediaProcessor {

	private static final String PROPERTIES_FILE_NAME = "MediaServer.properties";

	//private final static String CAPABILITY = "application/x-rtp, media=video, payload=123, clock-rate=90000, encoding-name=H264";
	private final static String CAPABILITY = "application/x-rtp, media=video, payload=123, clock-rate=90000, encoding-name=H264";
	// "video/x-raw-yuv,width=300,height=200, framerate=25/1"

	private String RECV_IP;
	private int RECV_PORT;	
	private int WIDTH 			= 704; // 704
	private int HEIGHT 			= 576; // 576
	private int PARTICIPANTS	= 4;
	private int DIVIDER			= (int) Math.sqrt(PARTICIPANTS);
	private String WELCOMETEXT;
	private int QUEUETIME		= 50;

	private int counter = 0;
	//private ParticipantVideo[] participants;

	/****************************************************************************
	 * GST_Variables
	 ****************************************************************************/
	private Pipeline pipe;
	// elements for sending out the mixed data
	private Element mixer;
	private Element encoder;
	private Element packetizer;
	private Element multiudpsink;
	// elements for the background data
	private Element background;
	private Element backgroundCapsfilter;
	private Element textoverlay;
	private Element timeoverlay;
	private Element backgroundQueue;

	private RTPBin rtpbin;
	private Element udpsrc;

	List<ParticipantVideo> participants = null;

	public void start(List<ParticipantVideo> videoParticipants) throws ConfigurationException, UnknownHostException, SocketException{

		participants = videoParticipants;

		// get configuration from Config File
		PropertiesConfiguration config = new PropertiesConfiguration();
		config.load(PROPERTIES_FILE_NAME);

		RECV_PORT=config.getInt("VIDEO_RECV_PORT");
		WELCOMETEXT=config.getString("WELCOMETEXT"); 

		RECV_IP = config.getString("IP_ADDR");

		// create the Pipeline
		pipe = new Pipeline("VideoForwarder");
		
		// create elements for sending out the data
		mixer = ElementFactory.make("videomixer", "mixer");
		mixer.set("background", "1");

		encoder = ElementFactory.make("x264enc", "encoder");
		encoder.set("tune", 4);				// 0 = none, 1 = stillimage, 2 = fastdecode, 4 = zerolatency
		encoder.set("bitrate", 4096);		// kbit/sec 1 - 102400
		encoder.set("byte-stream", true);		// 	true/false
		encoder.set("profile", 1);		// (0):No profile  (1): baseline  (2): main  (3): high  (4): high10
		encoder.set("speed-preset", 1);		// (0): No preset  (1):ultrafast  (2):superfast    (3): veryfast   (4): faster (5): fast  (6): medium  (7): slow (8): slower (9): veryslow  (10): placebo 
		encoder.set("psy-tune", 4);		// 
//		encoder.set("sliced-threads", true);

		packetizer = ElementFactory.make("rtph264pay", "packetizer");
		packetizer.set("pt", 123);
		packetizer.set("scan-mode", 1);

		multiudpsink = ElementFactory.make("udpsink", "sink");
		multiudpsink.set("sync", false);
		
		// create the elements for the background
		background = ElementFactory.make("videotestsrc", "Source");
		background.set("pattern", Background.BLACK);
		background.set("is-live", "true");

		backgroundCapsfilter = ElementFactory.make("capsfilter", "backgroundcaps");
		backgroundCapsfilter.setCaps(Caps.fromString("video/x-raw-yuv, width=" + WIDTH + ", height=" + HEIGHT));

		textoverlay = ElementFactory.make("textoverlay", "textoverlay");
		textoverlay.set("halign", "center");
		textoverlay.set("valignment", 4);
		textoverlay.set("text", WELCOMETEXT);
		textoverlay.set("shaded-background", "false");

		timeoverlay = ElementFactory.make("timeoverlay", "timeoverlay");
		timeoverlay.set("halign", "right");
		timeoverlay.set("valignment", 4);

		backgroundQueue = ElementFactory.make("queue", "background");
		backgroundQueue.set("leaky", 2);
		backgroundQueue.set("max-size-time", QUEUETIME * 1000);

		// create the elements for the participants

		ListIterator<ParticipantVideo> it1 = videoParticipants.listIterator();
		while(it1.hasNext()){
			ParticipantVideo pv = it1.next();
			// is the pv null?? 

			pv.setQueueSize(QUEUETIME);
			pv.setFrameBorderSize(2);
			pv.setFrameColor(FrameColor.BLACK);
			pv.init(true);	
		}
		
		// create the receiver for RTP
		rtpbin = (RTPBin) ElementFactory.make("gstrtpbin", "rtpbin");
		rtpbin.set("autoremove", true);

		udpsrc = ElementFactory.make("udpsrc", "udpsrc");
		udpsrc.set("uri", "udp://" + RECV_IP + ":" + RECV_PORT);
		udpsrc.set("caps", new Caps(CAPABILITY));

		pipe.addMany(rtpbin, udpsrc);
		// link the udpsrc with rtpbin
		Pad sinkpad = rtpbin.getRequestPad("recv_rtp_sink_0");
		Pad srcpad = udpsrc.getStaticPad("src");
		srcpad.link(sinkpad);
		
		// link the background
		pipe.addMany(background, backgroundCapsfilter, textoverlay, timeoverlay, backgroundQueue);	
		Element.linkMany(background, backgroundCapsfilter, textoverlay, timeoverlay, backgroundQueue);

		pipe.addMany(mixer, encoder, packetizer, multiudpsink);
		Element.linkMany(mixer, encoder, packetizer, multiudpsink);
		
		ListIterator<ParticipantVideo> it2 = videoParticipants.listIterator();
		while(it2.hasNext()){
			ParticipantVideo pv = it2.next();
			pipe.addMany(pv.getElements());
		}        

		ListIterator<ParticipantVideo> it3 = videoParticipants.listIterator();
		while(it3.hasNext()){
			ParticipantVideo pv = it3.next();
			Element.linkMany(pv.getElements());
		}


		Pad backgroundSrcPad = backgroundQueue.getStaticPad("src");
		Pad mixerSink0Pad = mixer.getRequestPad("sink_" + mixer.getPads().size());
		
		mixerSink0Pad.set("zorder", 0);
		mixerSink0Pad.set("alpha", 1);
		backgroundSrcPad.link(mixerSink0Pad);

		System.out.println(videoParticipants.size());

//		int index =  videoParticipants.size();
//		mixerSinkPads = new Pad[index];

		System.out.println("VideoBridge INIT");  

		GstDebugUtils.gstDebugBinToDotFile(pipe, 5, "videopipe_before_adding_participants");

		mixer.connect(new Element.PAD_ADDED() {
			
			@Override
			public void padAdded(Element element, Pad pad) {
				
				System.out.println("Mixer Pad added");
				
				// EOS event probe
				pad.addEventProbe(new Pad.EVENT_PROBE() {
					public boolean eventReceived(Pad pad, Event event) {
						boolean unlinkOk;
						
						System.out.println("Mixer Event received: " + event.toString());
						if (event instanceof EOSEvent) {
							System.out.println("EOS received");
//
//							// set source pad to blocked
							boolean isBlocked = pad.getPeer().setBlocked(true);
							System.out.println(pad.toString() + " succesfully blocked? "+isBlocked);
//
//							// unlink source pad from mixer sink
							unlinkOk = pad.getPeer().unlink(pad);
							System.out.println(pad.toString()+ " " + unlinkOk);
							
//							// remove respective mixer sink pad
							mixer.removePad(pad);
							System.out.println(pad.toString() + " succesfully removed");
//
//							//  TODO ??? pipe.remove(
//							//	gst_bin_remove(GST_BIN(pipeline), rec_bin);
//							//	gst_pad_set_blocked(srcpad, FALSE);

							return false;
						}
						return true;
					}
				});	
			}
		});
		
		/***************************************************
		 * create the participant-removed-listener
		 ****************************************************/		
		rtpbin.connect(new Element.PAD_REMOVED() {
			@Override
			public void padRemoved(Element elememt, Pad pad) {
				System.out.println("Videobridge: Pad " + pad.getName() + " autoremoved");
			}
		}); 

		rtpbin.connect(new Element.PAD_ADDED() {			
			@Override
			public void padAdded(Element element, Pad pad) {        		

				System.out.println("----------------------------------------------");
				System.out.println("NEW video PAD: " + pad.getName());

				if(counter > PARTICIPANTS){
					return;
				}

				if(pad.isLinked()){
					return;
				}
				
				//System.out.println("ParticipantVideo " + (counter + 1) + " added");
				//ParticipantVideo pvTmp = participants.get(counter);

				ParticipantVideo pvTmp = getNextFreeParticipantVideo();

				Element[] elements = pvTmp.getElements();
				rtpbin.link(elements[0]);	        		

				//int indexOfMixerSinkPads = mixer.getSinkPads().size()-1;
				Pad p = mixer.getRequestPad("sink_" + mixer.getPads().size());
		
				// When the participant gets added, there are already 2 sink pads (background & new requested)
				setPosition(p, mixer.getSinkPads().size()-2);

				if(!(p.isLinked())){

					PadLinkReturn linkOk = elements[elements.length-1].getStaticPad("src").link(p);
					System.out.println(linkOk);					
				}
				GstDebugUtils.gstDebugBinToDotFile(pipe, 3, "videopipe_after_adding_participant_XXX");
			}
		});

		pipe.setState(State.READY);         
		pipe.setState(State.PLAYING);
		System.out.println("VideoBridge PLAYING");

		//Send Event that ready for Focus to go on ...	
		MediaprocessingReadyEvent evt = new MediaprocessingReadyEvent(this);
		this.fireReadyEvent(evt);

		Gst.main();

		pipe.setState(State.NULL);
		pipe.stop();
		Gst.deinit();
	}


	protected ParticipantVideo getNextFreeParticipantVideo() {
		ParticipantVideo pv = null;

		ListIterator<ParticipantVideo> it = participants.listIterator();
		while(it.hasNext()){
			ParticipantVideo pvTmp = it.next();
			Element[] elements = pvTmp.getElements();

			// is 
			// elements[elements.length - 1] is the queue
			// elements[elements.length - 1].getStaticPad("src").getPeer().getParent().getType()

			if(!(elements[elements.length - 1].getStaticPad("src").isLinked())){
				pv = pvTmp;
				break;
			}
		}	
		return pv;
	}

	public void setSize(int[] resolution){
		WIDTH = resolution[0];
		HEIGHT = resolution[1];
	}

	public boolean setParticipants(int number){		
		if((Math.sqrt(number) == 2) ||
				(Math.sqrt(number) == 3) ||	
				(Math.sqrt(number) == 4) ||
				(Math.sqrt(number) == 5) ){
			this.PARTICIPANTS = (int) Math.sqrt(number);
			return true;
		}else{
			return false;
		}
	}

	public void setGreetings(String welcometext){
		WELCOMETEXT = welcometext;
	}

	private void setPosition(Pad mixerSinkPad, int counter){
		if (counter < 1){
			counter=0;
		}
		switch(counter){
		case 0:
			break;
		case 1:
			mixerSinkPad.set("xpos", WIDTH/DIVIDER);
			break;
		case 2:
			mixerSinkPad.set("ypos", HEIGHT/DIVIDER);	
			break;
		case 3:
			mixerSinkPad.set("xpos", WIDTH/DIVIDER);
			mixerSinkPad.set("ypos", HEIGHT/DIVIDER);
			break;
		}
	}

	public void addParticipant(String ip, int port){
		System.out.println(multiudpsink);
		multiudpsink.emit("add", ip, port);
	}

	public void removeParticipant(String callId) {
		// remove the participant with the given call-ID
		
		ParticipantVideo pv = getParticipantByCallId(callId);
		Element[] elements = pv.getElements();	

		Pad queueSourcePad = elements[elements.length - 1].getStaticPad("src");
		Pad mixerSinkPad = queueSourcePad.getPeer();
		
		if(!(mixerSinkPad==null)){
			mixerSinkPad.sendEvent(new EOSEvent());
		}
		
		String ip = pv.getIp();
		int port = pv.getPort();

		multiudpsink.emit("remove", ip, port);

		//queueSourcePad.unlink(mixerSinkPad);

		// try to remove, if found
		try	{

			//element[0] = depayloader
			Pad depaySinkPad;
			depaySinkPad = elements[0].getStaticPad("sink");

			String ssrc = getSsrcFromPadName(depaySinkPad.getPeer().getName());
			//System.out.println("SSRC of unlink-pad: " + ssrc);

			Pad recvRtpSrcPad = null;
			List<Pad> rtpBinSrcPads = rtpbin.getSrcPads();
			// cycle through all rtpbins pads to find the one with the name with the right SSRC
			for (Pad p : rtpBinSrcPads) {
				// TODO End of pad name is not always 0, depending on codec!!
				int indexOfLastUnderscore = p.getName().indexOf("_", 15);
				String padName = p.getName().substring(0, indexOfLastUnderscore);

				if (padName.equals("recv_rtp_src_0_" + ssrc)){
					recvRtpSrcPad = p;
				}
			}

			// unlink depay-src
			System.out.println("unlinking video depay-source-pad ...");
			recvRtpSrcPad.unlink(depaySinkPad);
			counter--;

			ListIterator<ParticipantVideo> it = participants.listIterator();
			while(it.hasNext()){
				ParticipantVideo pv1 = it.next();
				if (pv1.getCallId().equals(callId)){
					it.remove();
					System.out.println("removing ParticipantVideo from list...");
					break;
				}
			}
		} 
		catch (NullPointerException e){
			System.out.println("ParticipantVideo to remove not found!");
		}

		GstDebugUtils.gstDebugBinToDotFile(pipe, 5, "videopipe_after_removing_participant_" + mixer.getPads().size());

	}

	private ParticipantVideo getParticipantByCallId(String callId) {
		System.out.println("passed Call ID: " + callId);
		ParticipantVideo p=null;
		System.out.println("cycling through ParticipantVideo's ...");

		ListIterator<ParticipantVideo> it = participants.listIterator();
		while(it.hasNext()){
			ParticipantVideo pv = it.next();
			System.out.println("current ParticipantVideo's Call-ID: " + pv.getCallId());
			// TODO Null pointer Exception sometimes!? What to do, when P is not found?
			if (pv.getCallId().equals(callId)){
				p = pv;
				break;
			}
		}
		return p;
	}



}
