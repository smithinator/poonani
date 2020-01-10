package dakota.poonani;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import discord4j.core.object.entity.Member;

public class Routine implements java.io.Serializable
{
	/*
	 * Fields
	 */
	
	private static final long serialVersionUID = 1L;
	
	private List<String> triggers = new ArrayList<String>();
	private boolean checkPhraseExists = false;
	//maps user IDs to guild IDs
	private Map<Long, Long> users = new TreeMap<Long, Long>();
	private boolean tts = false;
	private String response = "";
	
	/*
	 * Constructors
	 */
	
	/**
	 * Creates a message-response Routine.
	 * @param triggers The list of strings that trigger the execution of this routine.
	 * @param checkPhraseExists False if the trigger should be found by contains() logic, true if it should be found by "word in a sentence" logic.
	 * @param users The whitelist of users to filter against for the execution of this routine.
	 * @param tts Whether the response should be text to speech
	 * @param response The desired response of this routine.
	 */
	public Routine(List<String> triggers, boolean checkPhraseExists, Map<Long, Long> users, boolean tts, String response)
	{
		this.triggers = triggers;
		this.checkPhraseExists = checkPhraseExists;
		this.users = users;
		this.tts = tts;
		this.response = response;
	}
	
	/*
	 * Getters/Setters
	 */
	
	/**
	 * Sets the triggers for this routine
	 * @param triggers The triggers for this routine
	 */
	public void setTriggers(List<String> triggers) {
		this.triggers = triggers;
	}
	
	/**
	 * Gets the triggers for this routine
	 * @return The triggers for this routine
	 */
	public List<String> getTriggers() {
		return triggers;
	}
	
	/**
	 * Sets whether the trigger should be found by contains or "word in a sentence" logic
	 * @param checkPhraseExists Whether the trigger should be found by contains or "word in a sentence" logic
	 */
	public void setCheckPhraseExists(boolean checkPhraseExists) {
		this.checkPhraseExists = checkPhraseExists;
	}
	
	/**
	 * Gets whether the trigger should be found by contains or "word in a sentence" logic
	 * @return Whether the trigger should be found by contains or "word in a sentence" logic
	 */
	public boolean getCheckPhraseExists() {
		return checkPhraseExists;
	}
	
	/**
	 * Sets the list of IDs of users (by user and guild ID) to be whitelisted for the routine
	 * @param users The list of IDs of users (by user and guild ID)to be whitelisted for the routine
	 */
	public void setUsers(Map<Long, Long> users) {
		this.users = users;
	}
	
	/**
	 * Gets the list of IDs of users (by user and guild ID) to be whitelisted for the routine
	 * @return The list of IDs of users (by user and guild ID) to be whitelisted for the routine
	 */
	public Map<Long, Long> getUsers() {
		return users;
	}
	
	/**
	 * Sets whether the response is text to speech
	 * @param tts Whether the response is text to speech
	 */
	public void setTTS(boolean tts) {
		this.tts = tts;
	}
	
	/**
	 * Gets whether the response is text to speech
	 * @return Whether the response is text to speech
	 */
	public boolean getTTS() {
		return tts;
	}
	
	/**
	 * Sets the response
	 * @param response The response
	 */
	public void setResponse(String response) {
		this.response = response;
	}
	
	/**
	 * Gets the response
	 * @return The response
	 */
	public String getResponse() {
		return response;
	}
	
	/*
	 * Methods
	 */
	
	/**
	 * Returns whether the beginning of message is "phrase" followed by ' ', ',', '.', or '!'
	 * @param message The message to check
	 * @param phrase The phrase to look for
	 * @return Whether the beginning of message is "phrase" followed by ' ', ',', '.', or '!'
	 */
	public boolean atBeginning(String message, String phrase)
	{
		if(message.length() <= phrase.length()) return false;
		if((message.substring(0,phrase.length()) == phrase) && (message.charAt(phrase.length()) == ' ' || message.charAt(phrase.length()) == ',' || message.charAt(phrase.length()) == '.' || message.charAt(phrase.length()) == '!')) return true;
		return false;
	}

	/**
	 * Returns whether message contains phrase preceded by space or newline, and followed by space or punctuation
	 * @param message The message to check
	 * @param phrase The phrase to look for
	 * @return Whether message contains phrase preceded by space or newline, and followed by space or punctuation
	 */
	public boolean atMiddle(String message, String phrase)
	{
		if( message.contains(' '+phrase+' ') || message.contains(' '+phrase+',') || message.contains(' '+phrase+'.') || message.contains(' '+phrase+'!')
		|| message.contains(' '+phrase+'\n') || message.contains('\n'+phrase+' ') || message.contains('\n'+phrase+'\n') || message.contains('\n'+phrase+',')
		|| message.contains('\n'+phrase+'.') || message.contains('\n'+phrase+'!')
		)	return true;
		return false;
	}

	/**
	 * Returns whether the end of message is " phrase", " phrase.", or " phrase!"
	 * @param message The message to check
	 * @param phrase The phrase to look for
	 * @return Whether the end of message is " phrase", " phrase.", or " phrase!"
	 */
	public boolean atEnd(String message, String phrase)
	{
		if(message.length() <= phrase.length()+1) return false;
		String periodPhrase = phrase + '.';
		String exPhrase = phrase + '!';
		if((message.substring(message.length()-phrase.length(),message.length()-1).equalsIgnoreCase(phrase)) || (message.substring(message.length()-phrase.length()-1,message.length()-1).equalsIgnoreCase(periodPhrase)) || (message.substring(message.length()-phrase.length()-1,message.length()-1).equalsIgnoreCase(exPhrase))) return true;
		return false;
	}

	/**
	 * Returns whether message consists of phrase, begins with phrase, contains " phrase" followed by ' ', ',', '.', or '!', or ends with phrase followed by nothing, '.' or '!'
	 * @param message The message to check
	 * @param phrase The phrase to look for
	 * @return Whether message consists of phrase, begins with phrase, contains " phrase" followed by ' ', ',', '.', or '!', or ends with phrase followed by nothing, '.' or '!'
	 */
	public boolean phraseExists(String message, String phrase) //
	{
		if(!message.contains(phrase)) return false; //avoid doing the numerous following searches if phrase is totally nonexistent
		return (message.equalsIgnoreCase(phrase) || atBeginning(message, phrase) || atMiddle(message, phrase) || atEnd(message, phrase));
	}
	
	/**
	 * Returns true if message contains a trigger using the appropriate logic based on checkPhraseExists, and the sender is in the whitelist
	 * @param message The message to check
	 * @param sender The sender of the message
	 * @return True if message contains a trigger using the appropriate logic based on checkPhraseExists, and the sender is in the whitelist
	 */
	public boolean findTrigger(String message, Member sender)
	{
		if(users.size() > 0 || !users.containsKey(sender.getId().asLong())) {
			return false;
		}
		for(String t : triggers)
		{
			if(checkPhraseExists && phraseExists(message, t) || (!checkPhraseExists && message.contains(t)))
			{
				return true;
			}
		}
		return false;
	}
}