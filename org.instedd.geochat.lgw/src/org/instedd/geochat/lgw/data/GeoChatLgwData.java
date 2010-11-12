package org.instedd.geochat.lgw.data;

import java.util.UUID;

import org.instedd.geochat.lgw.Uris;
import org.instedd.geochat.lgw.data.GeoChatLgw.IncomingMessages;
import org.instedd.geochat.lgw.data.GeoChatLgw.OutgoingMessages;
import org.instedd.geochat.lgw.msg.Message;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.telephony.SmsMessage;

public class GeoChatLgwData {
	
	private final static ContentValues NOT_BEING_SENT = new ContentValues();
	static {
		NOT_BEING_SENT.put(OutgoingMessages.SENDING, 0);
	}
	
	private final static ContentValues BEING_SENT = new ContentValues();
	static {
		BEING_SENT.put(OutgoingMessages.SENDING, 1);
	}
	
	private final Context context;
	private final Object notSendingLock = new Object();

	public GeoChatLgwData(Context context) {
		this.context = context;		
	}
	
	public void deleteOutgoingMessage(String guid) {
		context.getContentResolver().delete(Uris.outgoingMessage(guid), null, null);
	}
	
	public void markOutgoingMessageAsBeingSent(String guid) {
		synchronized(notSendingLock) {
			this.context.getContentResolver().update(Uris.outgoingMessage(guid), NOT_BEING_SENT, null, null);
		}
	}
	
	public void createIncomingMessage(SmsMessage message, String fromNumber) {
		String guid = UUID.randomUUID().toString();
        String from = message.getOriginatingAddress();
        String to = fromNumber;
        String text = message.getMessageBody();
        long when = message.getTimestampMillis();
		
		context.getContentResolver().insert(IncomingMessages.CONTENT_URI, 
        		Message.toContentValues(guid, from, to, text, when));
	}
	
	public Message[] getIncomingMessages() {
		Cursor c = context.getContentResolver().query(IncomingMessages.CONTENT_URI, Message.PROJECTION, null, null, null);
		int count = c.getCount();
		if (count == 0)
			return null;
		
		try {
			Message[] incoming = new Message[count];
			for (int i = 0; c.moveToNext(); i++)
				incoming[i] = Message.readFrom(c);
			return incoming;
		} finally {
			c.close();
		}
	}
	
	public void deleteIncomingMessageUpTo(String guid) {
		context.getContentResolver().delete(Uris.incomingMessageBefore(guid), null, null);
	}
	
	public Message[] getOutgoingMessagesNotBeingSentAndMarkAsBeingSent() {
		synchronized(notSendingLock) {
			Cursor c = context.getContentResolver().query(Uris.OutgoingMessagesNotBeingSent, Message.PROJECTION, null, null, null);
			try {
				int count = c.getCount();
				if (count == 0)
					return null;
				
				context.getContentResolver().update(Uris.OutgoingMessagesNotBeingSent, BEING_SENT, null, null);
				
				Message[] outgoing = new Message[count];
				for (int i = 0; c.moveToNext(); i++) {
					outgoing[i] = Message.readFrom(c);
				}
				return outgoing;
			} finally {
				c.close();
			}
		}
	}
	
	public String createOutgoingMessagesAsBeingSent(Message[] outgoing) {
		if (outgoing == null || outgoing.length == 0)
			return null;
			
		ContentValues[] values = new ContentValues[outgoing.length];
		for (int i = 0; i < outgoing.length; i++) {
			values[i] = outgoing[i].toContentValues();
			values[i].put(OutgoingMessages.SENDING, 1);
		}
		context.getContentResolver().bulkInsert(OutgoingMessages.CONTENT_URI, values);
		return outgoing[outgoing.length - 1].guid;
	}

}
