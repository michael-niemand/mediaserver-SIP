package de.fhffm.research.mediaserver;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.configuration.ConfigurationException;

public class ConferenceRoom {

	private String roomName = null;
	private int MAX_MEMBERS;

	private AudioMixer audioMixer = new AudioMixer();
	private VideoBridge videoBridge = new VideoBridge();
	
	private boolean audioReady;
	private boolean videoReady;

	public boolean isAudioReady() {
		return audioReady;
	}
	public void setAudioReady(boolean audioReady) {
		this.audioReady = audioReady;
	}
	public boolean isVideoReady() {
		return videoReady;
	}
	public void setVideoReady(boolean videoReady) {
		this.videoReady = videoReady;
	}
	
	public boolean isRoomReady() {
		return videoReady & audioReady;
	}

	private List<ConferenceMember> conferenceMembers = new ArrayList<ConferenceMember>();

	public AudioMixer getAudioMixer() {
		return audioMixer;
	}
	public void setAudioMixer(AudioMixer audioMixer) {
		this.audioMixer = audioMixer;
	}
	public VideoBridge getVideoBridge() {
		return videoBridge;
	}
	public void setVideoBridge(VideoBridge videoBridge) {
		this.videoBridge = videoBridge;
	}
	public List<ConferenceMember> getConferenceMembers() {
		return conferenceMembers;
	}
	public void setConferenceMembers(List<ConferenceMember> conferenceMembers) {
		this.conferenceMembers = conferenceMembers;
	}

	public String getRoomName() {
		return roomName;
	}
	public void setRoomName(String roomName) {
		this.roomName = roomName;
	}

	public ConferenceRoom(int maxMembers, String name) {
		MAX_MEMBERS = maxMembers;
		this.setRoomName(name);
		init();
	}	

	public void init(){
		//create all members
		for(int i=0; i<MAX_MEMBERS; i++){
			conferenceMembers.add(new ConferenceMember());
		}

		final List<ParticipantAudio> pa = this.getAudioParticipants();
		final List<ParticipantVideo> pv = this.getVideoParticipants();

		// Initialize Audiomixer and Videobridge in different threads	
		Runnable rAudio = new Runnable() {
			public void run() {
				while (true) {
					try {

						audioMixer.start(pa);

					} catch (ConfigurationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (UnknownHostException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (SocketException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		};
		Runnable rVideo = new Runnable() {
			public void run() {
				while (true) {
					try {

						videoBridge.start(pv);
						videoBridge.setSize(ScreenResolution._4CIF_4SIF_625);
						
					} catch (ConfigurationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (UnknownHostException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (SocketException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		};
		Thread thr1 = new Thread(rAudio);
		Thread thr2 = new Thread(rVideo);
		thr1.start();
		thr2.start();
		
		videoBridge.addMyEventListener(new MediaprocessingEventListener() {
			@Override
			public void myEventOccurred(MediaprocessingReadyEvent evt) {
				System.out.println("video processing is ready");
				videoReady = true;
			}
		});
		
		audioMixer.addMyEventListener(new MediaprocessingEventListener() {
			@Override
			public void myEventOccurred(MediaprocessingReadyEvent evt) {
				System.out.println("audio processing is ready");
				audioReady = true;
			}
		});
				
		
	}
	
	
	public List<ParticipantAudio> getAudioParticipants(){

		ListIterator<ConferenceMember> it = this.conferenceMembers.listIterator();
		List<ParticipantAudio> pa = new ArrayList<ParticipantAudio>();

		while(it.hasNext()){
			// if senders IP is in list of clients, authorized via SIP, forward RTP to localhost
			ConferenceMember element = it.next();
			pa.add(element.getAudio());
		}
		return pa;
	}

	public List<ParticipantVideo> getVideoParticipants(){

		ListIterator<ConferenceMember> it = this.conferenceMembers.listIterator();
		List<ParticipantVideo> pv = new ArrayList<ParticipantVideo>();

		while(it.hasNext()){
			// if senders IP is in list of clients, authorized via SIP, forward RTP to localhost
			ConferenceMember element = it.next();
			pv.add(element.getVideo());
		}
		return pv;
	}

	public boolean slotsAvailable() {

		boolean freeSlots = false;		
		ListIterator<ConferenceMember> it = this.conferenceMembers.listIterator();

		while(it.hasNext()){
			ConferenceMember c = it.next();
			if(!(c.hasParticipant())){
				freeSlots=true;
			}
		}
		return freeSlots;
	}

	public void addParticipant(String remoteIp, int audioRemotePort, int videoRemotePort, String callId, String participantName){

		// get next participant that has no data yet
		ListIterator<ParticipantAudio> itA = this.getAudioParticipants().listIterator();
		while(itA.hasNext()){
			ParticipantAudio pa = itA.next();
			// is the SIP-data already set on this one? 
			if(pa.getCallId()==null){
				pa.setCallId(callId);
				pa.setIp(remoteIp);
				pa.setPort(audioRemotePort);
				break;
			}
			
		}
		//... and video
		ListIterator<ParticipantVideo> itV = this.getVideoParticipants().listIterator();
		while(itV.hasNext()){
			ParticipantVideo pv = itV.next();
			// is the SIP-data already set on this one? 
			if(pv.getCallId()==null){
				// no: 
				pv.setCallId(callId);
				pv.setIp(remoteIp);
				pv.setPort(videoRemotePort);
				pv.setName(participantName);
				videoBridge.addParticipant(remoteIp, videoRemotePort);
				break;
			}
		}
	}

	public void removeParticipant(String callId){
		System.out.println("Trying to remove participant ...");
		audioMixer.removeParticipant(callId);
		videoBridge.removeParticipant(callId);
		
		ParticipantVideo pv = getVideoParticipantByCallId(callId);
		pv.setCallId(null);
		pv.setName(null);
		
		ParticipantAudio pa = getAudioParticipantByCallId(callId);
		pa.setCallId(null);
		pa.setIp(null);
		pa.setPort(0);		

	}

	public ParticipantAudio getAudioParticipantByCallId(String callId) {
		System.out.println("passed Call ID: " + callId);
		ParticipantAudio p=null;
		System.out.println("cycling through Pa's ...");

		ListIterator<ParticipantAudio> it = getAudioParticipants().listIterator();
		while(it.hasNext()){
			ParticipantAudio pa = it.next();
			if (pa.getCallId().equals(callId)){
				p = pa;
				break;
			}
		}
		return p;
	}

	public ParticipantVideo getVideoParticipantByCallId(String callId) {
		System.out.println("passed Call ID: " + callId);
		ParticipantVideo p=null;
		System.out.println("cycling through Pv's ...");

		ListIterator<ParticipantVideo> it = getVideoParticipants().listIterator();
		while(it.hasNext()){
			ParticipantVideo pv = it.next();
			if (pv.getCallId().equals(callId)){
				p = pv;
				break;
			}
		}
		return p;
	}


}
