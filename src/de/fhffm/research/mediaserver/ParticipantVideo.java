package de.fhffm.research.mediaserver;

import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;

public class ParticipantVideo extends Participant {
	
	private int width;
	private int height;
	private int time = 50;
	private int frame_color = 1;
	private int border_size	= 2;
	private String name="unknown";
	
	private boolean frame = false;
	
	private Element depacketizer;
	private Element decoder;
	private Element videoscale;
	private Element capsfilter;
	private Element videobox;
	private Element textoverlay;
	private Element queue;
	private Element colorspace;
	
	private final static int MSECOND = 1000; 
	
	/**
	 * Creates an instance of participant. The size of the video <br>
	 * has to be defined.
	 * @param width
	 * @param height
	 */
	public ParticipantVideo(int width, int height){
		setSize(width, height);
	}
	
	private void setSize(int width, int height){
		this.width = width;
		this.height = height;
	}
	
	public void init(boolean frame){
		long identifier = System.nanoTime();
		
		depacketizer = ElementFactory.make("rtph264depay", "depacketizer" + identifier);
		decoder = ElementFactory.make("ffdec_h264", "decoder" + identifier);
	    videoscale = ElementFactory.make("videoscale", "videoscale" + identifier);
	    capsfilter = ElementFactory.make("capsfilter", "capsfilter" + identifier);
	    capsfilter.setCaps(Caps.fromString("video/x-raw-yuv, width=" + width + ", height=" + height));
	    colorspace = ElementFactory.make("ffmpegcolorspace", "colorspace"+identifier);

	    	    
	    if(frame){
	    	this.frame = frame;
	    	videobox = ElementFactory.make("videobox", "videobox" + identifier);
	    	videobox.set("border-alpha", "1");				
	    	videobox.set("alpha", "1.0");					
	    	videobox.set("top", "-" + this.border_size);
	    	videobox.set("left", "-" + this.border_size);
	    	videobox.set("right", "-" + this.border_size);
	    	videobox.set("bottom", "-" + this.border_size);	
	    	videobox.set("fill", frame_color);						
	    }
	    if(this.name != null){
	        textoverlay = ElementFactory.make("textoverlay", "textoverlay" + identifier);
	        textoverlay.set("halign", "center");
	        textoverlay.set("valignment", 1);
	        textoverlay.set("text", this.name);
	        textoverlay.set("shaded-background", "true");
	        textoverlay.set("font-desc", "Sans 14");
	    }
		queue = ElementFactory.make("queue", "queue" + identifier);
		queue.set("leaky", 0);
		queue.set("max-size-time", time * MSECOND); 
	}

	public Element[] getElements(){
		Element[] elements;
		if(this.frame && this.name != null){
			elements = new Element[8];
			elements[0] = depacketizer;
			elements[1] = decoder;
			elements[2] = videoscale;
			elements[3] = capsfilter;
			elements[4] = videobox;
			elements[5] = textoverlay;
			elements[6] = colorspace;
			elements[7] = queue;
		}else if(this.frame && this.name == null){
			elements = new Element[7];
			elements[0] = depacketizer;
			elements[1] = decoder;
			elements[2] = videoscale;
			elements[3] = capsfilter;
			elements[4] = videobox;
			elements[5] = colorspace;
			elements[6] = queue;
		}else if(!this.frame && this.name == null){
			elements = new Element[7];
			elements[0] = depacketizer;
			elements[1] = decoder;
			elements[2] = videoscale;
			elements[3] = capsfilter;
			elements[4] = textoverlay;
			elements[5] = colorspace;
			elements[6] = queue;
		}else{
			elements = new Element[6];
			elements[0] = depacketizer;
			elements[1] = decoder;
			elements[2] = videoscale;
			elements[3] = capsfilter;
			elements[4] = colorspace;
			elements[5] = queue;
//			elements = new Element[5];
//			elements[0] = depacketizer;
//			elements[1] = decoder;
//			elements[2] = videoscale;
//			elements[3] = capsfilter;
//			elements[4] = queue;
//			elements = new Element[3];
//			elements[0] = depacketizer;
//			elements[1] = decoder;
//			elements[2] = queue;
		}		
		return elements;
	}
	
	public void setQueueSize(int time){
		if(time < 1){
			this.time = 1;
		}else if(time > 1000){
			this.time = 1000;
		}else{
			this.time = time;
		}
	}
	
	public void setFrameColor(int frame_color){
		this.frame_color = frame_color;
	}
	
	public void setFrameBorderSize(int size){
		if(size < 1){
			this.border_size = 1;
		}else if(size > 25){
			this.border_size = 25;
		}else{
			this.border_size = size;
		}
	}
	
	public void setName(String userName){
		System.out.println("setting name: " + userName);
		this.name = userName;
        textoverlay.set("text", this.name);

	}
	
}
