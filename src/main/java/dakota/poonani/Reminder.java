package dakota.poonani;

import java.time.LocalDateTime;

import discord4j.core.object.util.Snowflake;

public class Reminder implements java.io.Serializable
{
	/*
	 * Fields
	 */
	
	private static final long serialVersionUID = 1L;
	
	private String event;
	private LocalDateTime time;
	private boolean mention;
	//ordinary longs are used rather than Snowflakes because Snowflake is not Serializable
	private long userId;
	private long channelId;
	
	/*
	 * Constructors
	 */
	
	/**
	 * Creates a Reminder
	 * @param event The event of which to be reminded
	 * @param time The time at which to be reminded
	 * @param mention Whether the user should be mentioned in the message when the reminder is triggered
	 * @param userId The id of the user to remind
	 * @param channelId The channelId of the DM/guild channelthe reminder was initiated from
	 */
	Reminder(String event, LocalDateTime time, boolean mention, long userId, long channelId)
	{
		this.event = event;
		this.time = time;
		this.mention = mention;
		this.userId = userId;
		this.channelId = channelId;
	}
	
	/*
	 * Getters/Setters
	 */
	
	/**
	 * Sets the reminder event
	 * @param event The reminder event
	 */
	public void setEvent(String event) {
		this.event = event;
	}

	/**
	 * Returns the reminder event
	 * @return The reminder event
	 */
	public String getEvent() {
		return event;
	}

	/**
	 * Sets the reminder time
	 * @param time The time of the event
	 */
	public void setTime(LocalDateTime time) {
		this.time = time;
	}

	/**
	 * Returns the reminder time
	 * @return The reminder time
	 */
	public LocalDateTime getTime() {
		return time;
	}

	/**
	 * Sets whether or not the user should be mentioned when the reminder is triggered
	 * @param mention Whether the user should be mentioned
	 */
	public void setMention(boolean mention) {
		this.mention = mention;
	}

	/**
	 * Returns whether or not the user should be mentioned when the reminder is triggered
	 * @return Whether the user should be mentioned
	 */
	public boolean getMention() {
		return mention;
	}

	/**
	 * Sets the reminder user's id
	 * @param userId The id of the user to be reminded
	 */
	public void setUserId(long userId) {
		this.userId = userId;
	}

	/**
	 * Returns the reminder user's id
	 * @return The reminder user's id
	 */
	public long getUserId() {
		return userId;
	}

	/**
	 * Sets the reminder channel's ID
	 * @param channelId The id of the channel to remind in
	 */
	public void setChannelId(long channelId) {
		this.channelId = channelId;
	}

	/**
	 * Returns the id of the channel to remind in
	 * @return The id of the channel to remind in
	 */
	public long getChannelId() {
		return channelId;
	}
	
	/*
	 * Methods
	 */
	
	/**
	 * Gets the Snowflake object for the user's ID
	 * @return The Snowflake object for the user's ID
	 */
	public Snowflake getUserSnowflake() {
		return Snowflake.of(userId);
	}
	
	/**
	 * Gets the Snowflake object for the channel's ID
	 * @return The Snowflake object for the channel's ID
	 */
	public Snowflake getChannelSnowflake() {
		return Snowflake.of(channelId);
	}
}