package de.fhffm.research.mediaserver;

import org.gstreamer.Pipeline;

public class MediaProcessor {

	private final static String PROPERTIES_FILE_NAME = null;

	private final static String CAPABILITY = null;

	private final static int MSECOND = 1000; 

	private int RECV_PORT;
	private String RECV_IP;
	private int maxParticipants;
	private int QUEUETIME;


	protected javax.swing.event.EventListenerList listenerList = new javax.swing.event.EventListenerList();

	
	private int counter = 0;

	private Pipeline pipe;

	public int getRECV_PORT() {
		return RECV_PORT;
	}

	public void setRECV_PORT(int rECV_PORT) {
		RECV_PORT = rECV_PORT;
	}

	public String getRECV_IP() {
		return RECV_IP;
	}

	public void setRECV_IP(String rECV_IP) {
		RECV_IP = rECV_IP;
	}

	public int getMaxParticipants() {
		return maxParticipants;
	}

	public void setMaxParticipants(int maxParticipants) {
		this.maxParticipants = maxParticipants;
	}

	public int getQUEUETIME() {
		return QUEUETIME;
	}

	public void setQUEUETIME(int qUEUETIME) {
		QUEUETIME = qUEUETIME;
	}

	public int getCounter() {
		return counter;
	}

	public void setCounter(int counter) {
		this.counter = counter;
	}

	public Pipeline getPipe() {
		return pipe;
	}

	public void setPipe(Pipeline pipe) {
		this.pipe = pipe;
	}

	public static String getPropertiesFileName() {
		return PROPERTIES_FILE_NAME;
	}

	public static String getCapability() {
		return CAPABILITY;
	}

	public static int getMsecond() {
		return MSECOND;
	}

	protected String getSsrcFromPadName(String padname){
		String returnValue;
		
		int beginIndex=padname.indexOf("_", 13)+1;
		int endIndex = padname.indexOf("_", 15);
		try {
			returnValue = padname.substring(beginIndex, endIndex);
		} catch (StringIndexOutOfBoundsException e) {
			returnValue = null;
		}
		return returnValue;
	}

	public void removeParticipant(String callId) {
		// TODO Auto-generated method stub
		
	}


    public void addMyEventListener(MediaprocessingEventListener listener) {
        listenerList.add(MediaprocessingEventListener.class, listener);
    }
	
    public void removeMyEventListener(MediaprocessingEventListener listener) {
        listenerList.remove(MediaprocessingEventListener.class, listener);
    }
	
    void fireReadyEvent(MediaprocessingReadyEvent evt) {
        Object[] listeners = listenerList.getListenerList();
        // Each listener occupies two elements - the first is the listener class
        // and the second is the listener instance
        for (int i=0; i<listeners.length; i+=2) {
            if (listeners[i]==MediaprocessingEventListener.class) {
                ((MediaprocessingEventListener)listeners[i+1]).myEventOccurred(evt);
            }
        }
    }




}
