package de.fhffm.research.mediaserver;

public class ConferenceMember {

	private ParticipantAudio audio; // = new ParticipantAudio();

	private ParticipantVideo video; 
	
	public ParticipantAudio getAudio() {
		return audio;
	}
	public void setAudio(ParticipantAudio audio) {
		this.audio = audio;
	}
	public ParticipantVideo getVideo() {
		return video;
	}
	public void setVideo(ParticipantVideo video) {
		this.video = video;
	}
	
	public ConferenceMember(){
		init();
	}
	
	public void init(){
		// System.out.println("creating audio & video participants ...");
		audio = new ParticipantAudio();
		video = new ParticipantVideo(ScreenResolution.CIF_SIF_625[0], ScreenResolution.CIF_SIF_625[1]);
		//video = new ParticipantVideo(ScreenResolution.SCIF[0], ScreenResolution.SCIF[1]);
		//video = new ParticipantVideo(320, 240);
		
	}
	
	public boolean hasParticipant(){
		boolean result = false;
		//System.out.println("audio callID null: "+(audio.callId==null));
		//System.out.println("video callID: "+video.callId);
		
		if(!(audio.callId==null || video.callId==null)){
			result = true;
		}
		
		return result;
	}
	
}
