package dakota.poonani;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;

import discord4j.core.DiscordClient;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.GuildMessageChannel;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.PermissionSet;
import discord4j.core.object.util.Snowflake;
import discord4j.voice.AudioProvider;
import discord4j.voice.VoiceConnection;

public class GuildHandler {
	
	/*
	 * Fields
	 */
	
	private static Logger logger = LoggerFactory.getLogger(GuildHandler.class);
	
	private enum LoggerLevel {
		ERROR, WARN, INFO, DEBUG
	}
	
	public static DiscordClient client;
	private static PrivateChannel authorPM;
	
	//Guild-specific info
	private Guild guild;
	private Role adminRole;
	private Role colorPermRole;
	private Set<Role> colorRoles;
	private List<Routine> routines;
	private List<Reminder> reminders;
	private boolean welcomeNewUser = true;
	private boolean newUserMention = true;
	private String newUserMessage = "https://www.youtube.com/watch?v=Za2PJnCAkUA";
	
	//Storage channel message IDs
	private Message idStorageMessage;
	private Long adminRoleMessageId;
	private Long colorPermRoleMessageId;
	private HashMap<Long, Long> colorRoleMessageIds;
	private HashMap<Long, Long> routineMessageIds;
	private HashMap<Long, Long> reminderMessageIds;
	private Long newUserMessageId;
	
	//Storage
	private TextChannel storageChannel;
	private static final String STORAGE_CHANNEL = "poonaniStorage";
	private static final String STORAGE_CHANNEL_DESC = "Where Poonani Tsunami stores information";
	private static final String STORAGE_CHANNEL_CREATE_REASON = "Creating a channel to store information for this server (hidden by default, can be changed)";
	
	//Audio
	public static AudioPlayerManager audioPlayerManager;
	private AudioPlayer audioPlayer;
	private LavaPlayerAudioProvider lavaPlayer = new LavaPlayerAudioProvider();
	private TrackScheduler trackScheduler = new TrackScheduler();
	private LinkedBlockingQueue<AudioTrack> trackQueue = new LinkedBlockingQueue<AudioTrack>();
	private VoiceConnection connection;
	
	private final class LavaPlayerAudioProvider extends AudioProvider {
		private final MutableAudioFrame frame = new MutableAudioFrame();
		
		public LavaPlayerAudioProvider() {
			super(ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize()));
	        frame.setBuffer(getBuffer());
		}
		
		@Override
		public boolean provide() {
			final boolean didProvide = audioPlayer.provide(frame);
			if(didProvide) {
				getBuffer().flip();
			}
			return didProvide;
		}
	}
	
	private final class TrackScheduler extends AudioEventAdapter {
		private void playNextTrack() {
			AudioTrack next = trackQueue.poll();
			if(next != null) audioPlayer.playTrack(next);
		}
		
		public void addTrack(AudioTrack track) {
			//if nothing is currently playing, immediately play the track, otherwise queue it
			if(audioPlayer.getPlayingTrack() == null) {
				audioPlayer.playTrack(track);
			} else {
				try {
					trackQueue.put(track);
				} catch (InterruptedException e) {
					e.getMessage();
				}
			}
		}
		
		@Override
		public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
			if (endReason.mayStartNext) {
				playNextTrack();
			}
		}

		@Override
		public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
			playNextTrack();
		}

		@Override
		public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
			playNextTrack();
		}
	}
	
	/*
	 * Constructors
	 */
	
	/**
	 * Constructs the handler for a given guild and performs all necessary initialization
	 * @param guild
	 */
	public GuildHandler(Guild guild) {
		this.guild = guild;
		logWithGuildId(LoggerLevel.DEBUG, "Starting handler initialization. beginning storage channel and admin role initialization");
		retrieveStorageChannelAndAdminRole();
//		logWithGuildId(LoggerLevel.DEBUG, "Completed storage channel and admin role initialization, beginning color perm role initialization");
//		retrieveColorPermRole();
//		logWithGuildId(LoggerLevel.DEBUG, "Completed color perm role initialization, beginning color role initialization");
//		retrieveColorRoles();
//		logWithGuildId(LoggerLevel.DEBUG, "Completed color role initialization, beginning routine initialization");
//		retrieveRoutines();
//		logWithGuildId(LoggerLevel.DEBUG, "Completed routine initialization, beginning reminder initialization");
//		retrieveReminders();
//		logWithGuildId(LoggerLevel.DEBUG, "Completed reminder initialization, beginning user join message initialization");
//		retrieveUserJoinMessage();
//		logWithGuildId(LoggerLevel.DEBUG, "Completed user join message initialization, finished handler initialization");
		
		//send welcome message into storage channel tagging admin role, describing settings that can be set
		
		//initialize PM channel between poonani and me for automatic error reporting
		authorPM = client.getUserById(Snowflake.of(205232518696402944L)).flatMap(user -> user.getPrivateChannel()).block();
	}
	
	/*
	 * Getters/Setters
	 */
	
	/**
	 * Gets the guild for this handler
	 * @return the guild for this handler
	 */
	public Guild getGuild() {
		return guild;
	}
	
	/**
	 * Gets the Reminders for this handler
	 * @return the Reminders for this handler
	 */
	public List<Reminder> getReminders() {
		return reminders;
	}
	
	/*
	 * Utility methods
	 */
	
	private void logWithGuildId(LoggerLevel level, String message) {
		switch (level) {
			case ERROR:
				logger.error("Guild " + guild.getId().asLong() + ": " + message);
				break;
			case WARN:
				logger.warn("Guild " + guild.getId().asLong() + ": " + message);
				break;
			case INFO:
				logger.info("Guild " + guild.getId().asLong() + ": " + message);
				break;
			case DEBUG:
			default:
				logger.debug("Guild " + guild.getId().asLong() + ": " + message);
				break;
		}
	}
	
	private static boolean setContainsPermission(PermissionSet set, Permission permission) {
		//The permission string ANDed with a permission set containing the desired permission should equal 0 or a permission set of only the desired permission
		return set.and(PermissionSet.of(permission)).equals(PermissionSet.of(permission));
	}
	
	private static boolean permissionTest(Member member, Role permRole)
	{
		return member.getHighestRole().block().getPosition().block() >= permRole.getPosition().block();
	}
	
	//given a newline before the line with an ID, returns the ID by returning everything up until the next newline as a Long
	private Long getIdFromStorageMessage(int newlineIndex) {
		String storageMessage = idStorageMessage.getContent().get();
		storageMessage = storageMessage.substring(newlineIndex);
		return Long.valueOf(storageMessage.substring(0, storageMessage.indexOf("\n")));
	}
	
	private void createDefaultAdminRole() {
		adminRole = guild.createRole(spec -> spec.setName("Poonani Admin").setReason("No admin role for Poonani existed yet, created a default").setPermissions(PermissionSet.of(Permission.MANAGE_GUILD))).block();
		guild.getOwner().subscribe(owner -> owner.addRole(adminRole.getId()));
		logWithGuildId(LoggerLevel.DEBUG, "Created initial default admin role and assigned to guild owner successfully");
	}
	
	/*
	 * Storage methods
	 */
	
	//This method is separated from createDefaultAdminRole because during guild initialization the storage channel must be created in between the role's creation and storage
	private void storeAdminRole() {
		adminRoleMessageId = storageChannel.createMessage("ADMIN ROLE:\n" + adminRole.getId().asLong()).block().getId().asLong();
		if(adminRoleMessageId != null) {
			logWithGuildId(LoggerLevel.DEBUG, "Successfully stored the admin role information in the storage channel");
			
			//insert ID of admin role message into ID storage message
			idStorageMessage.edit(messageSpec -> {
				messageSpec.setContent(idStorageMessage.getContent().get() + "\nADMIN ROLE MESSAGE:\n" + adminRoleMessageId);
			});
		} else {
			logWithGuildId(LoggerLevel.ERROR, "Something went wrong while storing the admin role information in the storage channel");
		}
	}
	
	private void storeColorPermRole() {
		colorPermRoleMessageId = storageChannel.createMessage("COLOR PERM ROLE:\n" + colorPermRole.getId().asLong()).block().getId().asLong();
		if(colorPermRoleMessageId != null) {
			logWithGuildId(LoggerLevel.DEBUG, "Successfully stored the color perm role information in the storage channel");
			
			//insert ID of color perm role message into ID storage message
			idStorageMessage.edit(messageSpec -> {
				messageSpec.setContent(idStorageMessage.getContent().get() + "\nCOLOR PERM ROLE MESSAGE:\n" + colorPermRoleMessageId);
			});
		} else {
			logWithGuildId(LoggerLevel.ERROR, "Something went wrong while storing the color perm role information in the storage channel");
		}
	}
	
	private void storeColorRole(Role role)
	{
		
	}
	
	//usage: :addnew: :triggers: true/false users true/false response
	/**
	 * Validates and stores a new routine.
	 * @param routine
	 */
	private void storeRoutine(Routine routine)
	{
		//perm checks below in handleAdd
		String routineMessage = "ROUTINE:\n:addnew: ";
		for(String trigger : routine.getTriggers()) {
			routineMessage += trigger += " ";
		}
		routineMessage += String.valueOf(routine.getCheckPhraseExists()) + " ";
		for(Map.Entry<Long, Long> user : routine.getUsers().entrySet()) {
			routineMessage += user.getKey() + " ";
		}
		routineMessage += String.valueOf(routine.getTTS()) + " ";
		routineMessage += routine.getResponse();
		storageChannel.createMessage(routineMessage).block();
	}
	
	//usage: :remindme: HR:MN PM MonthName dd, yyyy true/false event; hour and day must be 2 digits, time zone must be US Central
	private void storeReminder(Reminder reminder)
	{
		//perm checks below in handleAdd
		storageChannel.createMessage("REMINDER:\n:remindme: "
				+ DateTimeFormatter.ofPattern("KK:mm a MMMM dd, yyyy").format(reminder.getTime()) + " "
				+ String.valueOf(reminder.getMention())
				+ reminder.getEvent()).block();
	}
	
	/*
	 * Retrieval methods
	 */
	
	/**
	 * These two processes are handled in the same method because they are interdependent.
	 * If they don't exist the admin role is created, first, but if they do exist the storage channel must be read first
	 * If the storage channel does exist, then all the pinned messages are searched to find the stored admin role. The admin role is in the format:
	 * ADMIN ROLE:
	 * [role id]
	 */
	private void retrieveStorageChannelAndAdminRole() {
		guild.getChannels().filter(channel -> channel instanceof TextChannel).filter(channel -> channel.getName().equals(STORAGE_CHANNEL)).collectList().subscribe(channels -> {
			if(channels.isEmpty()) {
				//new guild, create default admin role and assign to owner
				logWithGuildId(LoggerLevel.DEBUG, "Storage channel not found, creating it with perms for the lowest role with Manage Server permissions");
				
				//sort roles from lowest perms to highest; find the lowest role that has the Manage Server permission
				adminRole = guild.getRoles().sort((Role r1, Role r2) -> ((Long) r1.getPermissions().getRawValue()).compareTo((Long) r2.getPermissions().getRawValue())).filter(role -> setContainsPermission(role.getPermissions(), Permission.MANAGE_GUILD)).blockFirst();
				if(adminRole != null) {
					logWithGuildId(LoggerLevel.DEBUG, "Found an existing role with the Manage Server permission, assigned it to adminRole");
				} else {
					logWithGuildId(LoggerLevel.DEBUG, "Did not find an existing role with the Manage Server permission. Creating one and assigning it to the owner");
					createDefaultAdminRole();
					
					//create a new channel that is visible to adminRole and Poonani but not visible to @everyone role
					Set<PermissionOverwrite> perms = new HashSet<PermissionOverwrite>();
					perms.add(PermissionOverwrite.forRole(guild.getEveryoneRole().block().getId(), PermissionSet.none(), PermissionSet.of(Permission.VIEW_CHANNEL)));
					perms.add(PermissionOverwrite.forRole(adminRole.getId(), PermissionSet.of(Permission.VIEW_CHANNEL), PermissionSet.none()));
					perms.add(PermissionOverwrite.forMember(client.getSelf().block().asMember(guild.getId()).block().getId(), PermissionSet.of(Permission.VIEW_CHANNEL), PermissionSet.none()));
					storageChannel = guild.createTextChannel(spec -> spec.setName(STORAGE_CHANNEL).setTopic(STORAGE_CHANNEL_DESC).setPermissionOverwrites(perms).setReason(STORAGE_CHANNEL_CREATE_REASON)).block();
					if(storageChannel != null) {
						logWithGuildId(LoggerLevel.DEBUG, "Storage channel created successfully");
						
						//create the ID storage message
						idStorageMessage = storageChannel.createMessage("Stored information message IDs:").block();
						if(idStorageMessage != null) {
							logWithGuildId(LoggerLevel.DEBUG, "ID storage message created successfully");
						} else {
							logWithGuildId(LoggerLevel.DEBUG, "Something went wrong while creating the ID storage message");
							return;
						}
					} else {
						logWithGuildId(LoggerLevel.ERROR, "Something went wrong while creating the storage channel");
						return;
					}
				}
				storeAdminRole();
			} else {
				logWithGuildId(LoggerLevel.DEBUG, "Storage channel found successfully");
				storageChannel = (TextChannel) channels.get(0);
				
				//retrieve admin role
				try {
					storageChannel.getPinnedMessages().filter(message -> message.getContent().filter(content -> content.startsWith("ADMIN ROLE:\n")).isPresent()).subscribe(message -> {
						adminRole = client.getRoleById(guild.getId(), Snowflake.of(Long.valueOf(message.getContent().get().split("\n")[1]))).block();
						if(adminRole != null) {
							logWithGuildId(LoggerLevel.DEBUG, "Retrieved the admin role successfully");
						} else {
							logWithGuildId(LoggerLevel.ERROR, "Something went wrong while retrieving the admin role");
						}
					});
					if(adminRole == null) {
						logWithGuildId(LoggerLevel.DEBUG, "Storage channel exists but adminRole message does not exist, guild data may be in a bad state. Creating default role");
						createDefaultAdminRole();
						storeAdminRole();
					}
				} catch(Exception e) {
					logWithGuildId(LoggerLevel.ERROR, "Admin Role message retrieval failed:\n" + e.getMessage());
				}
			}
		});
	}
	
	/**
	 * Default behavior at initialization sets the color perm role to be the admin role.
	 * The color permission role is in the format:
	 * COLOR PERM ROLE:
	 * [role id]
	 */
	private void retrieveColorPermRole() {
		if(idStorageMessage.getContent().get().indexOf("COLOR PERM ROLE:\n") == -1) {
			colorPermRole = adminRole;
			colorPermRoleMessageId = storageChannel.createMessage("COLOR PERM ROLE:\n" + colorPermRole.getId().asLong()).block().getId().asLong();
			//idstoragemessage edit
			logWithGuildId(LoggerLevel.DEBUG, "New guild, set the color perm role to the admin role and stored it in the storage channel");
		} else {
			colorPermRoleMessageId = getIdFromStorageMessage(idStorageMessage.getContent().get().indexOf("COLOR PERM ROLE:\n") + 17);
			colorPermRole = client.getRoleById(guild.getId(), Snowflake.of(Long.valueOf(storageChannel.getMessageById(Snowflake.of(colorPermRoleMessageId)).block().getContent().get().split("\n")[1]))).block();
			if(colorPermRole != null) {
				logWithGuildId(LoggerLevel.DEBUG, "Successfully retrieved the existing color perm role");
			} else {
				logWithGuildId(LoggerLevel.DEBUG, "Something went wrong while attempting to retrieve the existing color perm role! Attempting to salvage by setting the color perm role to the admin role");
				colorPermRole = adminRole;
				colorPermRoleMessageId = storageChannel.createMessage("COLOR PERM ROLE:\n" + colorPermRole.getId().asLong()).block().getId().asLong();
				//idstoragemessage edit
			}
		}
	}
	
	/**
	 * One message contains COLOR ROLE MESSAGES:\n followed by newline delineated list of IDs
	 * Color roles are in the format:
	 * COLOR ROLE:
	 * [role id]
	 */
	private void retrieveColorRoles() {
		colorRoles = new HashSet<Role>();
		if(colorRoleMessageIds == null) {
			colorPermRoleMessageId = storageChannel.createMessage("COLOR PERM ROLE:\n" + colorPermRole.getId().asLong()).block().getId().asLong();
			logWithGuildId(LoggerLevel.DEBUG, "New guild, set the color perm role to the admin role and stored it in the storage channel");
		} else {
			
		}
		storageChannel.getPinnedMessages().filter(message -> message.getContent().filter(content -> content.startsWith("COLOR ROLE:\n")).isPresent()).subscribe(message -> {
			try {
				//get everything after the first newline, since in stored form the message should be COLOR ROLE:\n[actual message]
				colorRoles.add(client.getRoleById(guild.getId(), Snowflake.of(Long.valueOf(message.getContent().get().split("\n")[1]))).block());
				logWithGuildId(LoggerLevel.DEBUG, "Found a color role and successfully stored it");
			} catch(IllegalArgumentException e) {
				logWithGuildId(LoggerLevel.ERROR, "Color Role retrieval failed:\n" + e.getMessage());
			}
		});
	}
	
	/**
	 * At initialization, searches all the pinned messages of the storage channel to find stored Routines. Routines are in the format:
	 * ROUTINE:
	 * usual routine command e.g. :addnew: ...etc.
	 */
	private void retrieveRoutines() {
		routines = new LinkedList<Routine>();
		storageChannel.getPinnedMessages().filter(message -> message.getContent().filter(content -> content.startsWith("ROUTINE:\n")).isPresent()).subscribe(message -> {
			try {
				//get everything after the first newline, since in stored form the message should be ROUTINE:\n[actual message]
				routines.add(parseRoutine(message.getContent().get().split("\n")[1]));
				logWithGuildId(LoggerLevel.DEBUG, "Found a routine and successfully stored it");
			} catch(IllegalArgumentException e) {
				logWithGuildId(LoggerLevel.ERROR, "Routine message failed to parse:\n" + e.getMessage());
			}
		});
	}
	
	/**
	 * At initialization, searches all the pinned messages of the storage channel to find stored Reminders. Reminders are in the format:
	 * REMINDER:
	 * [userid]
	 * [channelid]
	 * usual reminder command e.g. :remindme: ...etc.
	 */
	private void retrieveReminders() {
		reminders = new LinkedList<Reminder>();
		storageChannel.getPinnedMessages().filter(message -> message.getContent().filter(content -> content.startsWith("REMINDER:\n")).isPresent()).subscribe(message -> {
			try {
				//get everything after the first three newlines, since in stored form the message should be REMINDER:\n[userid]\n[channelid]\n[actual message]
				String[] content = message.getContent().get().split("\n");
				//TODO: NULL CHECK!
				reminders.add(parseReminder(content[3], Long.valueOf(content[1]), (MessageChannel) client.getChannelById(Snowflake.of(Long.valueOf(content[2]))).block()));
				logWithGuildId(LoggerLevel.DEBUG, "Found a reminder and successfully stored it");
			} catch(IllegalArgumentException e) {
				logWithGuildId(LoggerLevel.ERROR, "Reminder message failed to parse:\n" + e.getMessage());
			}
		});
	}
	
	/**
	 * At initialization, searches all the pinned messages of the storage channel to find the stored user join message. The message is in the format:
	 * USER JOIN MESSAGE:
	 * whether this feature is enabled
	 * whether the user should be mentioned at the beginning of the message, as "true"/"false"
	 * The message
	 * TODO: log when no settings are found
	 */
	private void retrieveUserJoinMessage() {
		try {
			storageChannel.getPinnedMessages().filter(message -> message.getContent().filter(content -> content.startsWith("USER JOIN MESSAGE:\n")).isPresent()).subscribe(message -> {
				//get everything after the first newline, since in stored form the message should be COLOR ROLE:\ntrue/false\ntrue/false\n[actual message]
				String[] args = message.getContent().get().split("\n");
				welcomeNewUser = Boolean.valueOf(args[1]);
				newUserMention = Boolean.valueOf(args[2]);
				newUserMessage = args[3];
				logWithGuildId(LoggerLevel.DEBUG, "Found user join message settings and stored them successfully");
			});
		} catch(Exception e) {
			logWithGuildId(LoggerLevel.ERROR, "User join message retrieval failed:\n" + e.getMessage());
		}
	}
	
	/*
	 * Handler methods
	 */
	
	/**
	 * Prints a message to the system channel welcoming a new user, if the feature is enabled.
	 * May or may not mention the user depending on the mention setting.
	 * @param event The event for the member joining
	 */
	public void handle(MemberJoinEvent event)
	{
		if(!welcomeNewUser) return;
		Member newUser = event.getMember();
		if(newUserMention) {
			guild.getSystemChannel().block().createMessage(newUserMessage).block();
		} else {
			guild.getSystemChannel().block().createMessage(newUser.getMention() + " " + newUserMessage).block();
		}
	}
	
	private Routine parseRoutine(String message) {
		try
		{
			//triggers
			List<String> triggers = new LinkedList<String>();
			ArrayList<String> arguments = new ArrayList<String>(Arrays.asList(message.split(" ")));
			int nextIndex = 0; //essentially i in the following for loops, but usable outside of them
			for(int i = 1; !arguments.get(i).equals("true") && !arguments.get(i).equals("false") && i < arguments.size(); i++) //iterate until we reach the next true/false argument, begin at index 1 to skip :addnew:
			{
				if(!arguments.get(i).equals(":addnew:") && !arguments.get(i).equals(":remindme:") && !arguments.get(i).equals(":help:") && !arguments.get(i).equals(":?:")) triggers.add(arguments.get(i));
				nextIndex = i+1; //true/false value for phrase is after the last iteration of this loop
			}
			if(triggers.size() == 0)
			{
				throw new IllegalArgumentException("You entered no triggers or only special commands including :addnew:, :remindme:, :help:, and :?:, please check your syntax and try again.");
			}
			
			//parse logic
			boolean checkPhraseExists;
			if(nextIndex < arguments.size() && arguments.get(nextIndex).equals("true")) checkPhraseExists = true;
			else checkPhraseExists = false;
			
			//users
			Map<Long, Long> users = new TreeMap<Long, Long>();
			for(int i = nextIndex+1; !arguments.get(i).equals("true") && !arguments.get(i).equals("false") && i < arguments.size(); i++) //repeat the above process beginning after the previous true/false phrase
			{
				if(!arguments.get(i).equals("none")) users.put(Long.valueOf(arguments.get(i)), guild.getId().asLong());
				nextIndex = i+1; //true/false value for phrase is after the last iteration of this loop
			}
			
			//tts
			boolean tts = nextIndex < arguments.size() && arguments.get(nextIndex).equals("true");
			
			//response
			String response = "";
			for(int i = nextIndex+1; i < arguments.size(); i++) //increment once to get the next argument, then combine all remaining words into the response
			{
				response += arguments.get(i) + " "; //re-add the spaces that were removed by split()
			}
			response = response.substring(0,response.length()-1); //remove the last hanging " " from response
			return new Routine(triggers, checkPhraseExists, users, tts, response);
		}
		catch(Exception e)
		{
			throw new IllegalArgumentException("Something went wrong. Double check your input, the syntax is :addnew: :triggers: true/false userIDs true/false response.");
		}
	}
	
	private static String verifyRoutine(Routine newRoutine) {
		String verify = "Creating new routine with trigger(s):";
		for(String s : newRoutine.getTriggers())
		{
			verify += " " + s;
		}
		verify += "\nwith check for phrase value of: " + newRoutine.getCheckPhraseExists() + "\nwith users:";
		//map a Map<UserId, GuildId> to List<Username>
		List<String> usernames = newRoutine.getUsers().entrySet().stream().map(id -> client.getUserById(Snowflake.of(id.getKey())).block().asMember(Snowflake.of(id.getValue())).block().getUsername()).collect(Collectors.toList());
		for(String s : usernames)
		{
			verify += " " + s;
		}
		verify += "\nwith text to speech value of: " + newRoutine.getTTS() + "\nwith response: " + newRoutine.getResponse();
		return verify;
	}
	
	private static Reminder parseReminder(String message, Long userId, MessageChannel channel) {
		try
		{
			Pattern pattern = Pattern.compile("\\d{4}"); //find the year, the last part of the date
			Matcher matcher = pattern.matcher(message);
			String date = "";
			int lastDigit = 0;
			if(matcher.find())
			{
				lastDigit = matcher.start() + 3;
				date = message.substring(11, lastDigit + 1); //exclude ":remindme: " and continue to the last digit
			} else {
				throw new DataFormatException();
			}
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("KK:mm a MMMM dd, yyyy");
			Reminder newReminder = new Reminder(message.substring(matcher.start() + 5), LocalDateTime.from(formatter.parse(date)), true, userId, channel.getId().asLong());
			channel.createMessage("Okay, I'll remind you about \"" + newReminder.getEvent() + "\" at " + date).block();
		}
		catch(Exception e) {
			channel.createMessage("Sorry, I couldn't understand that date and time. The usage is :remindme: HR:MN PM MonthName dd, yyyy true/false event; hour and day must be 2 digits.").block();
		}
		return null;
	}
	
	/**
	 * 
	 */
	private static void handleHelpMessage(MessageCreateEvent event, Member sender, List<Routine> routines) {
		event.getMessage().delete();
		PrivateChannel PM = sender.getPrivateChannel().block();
		Properties resources = new Properties();
		//TODO: is this the proper way of doing this? how does collaborati load application resource strings (strings with whitespace, in particular)
		FileInputStream in;
		try {
			in = new FileInputStream("src/main/resources/applicationResources.txt");
			resources.load(in);
		} catch (Exception e) {
			e.printStackTrace();
			PM.createMessage("Sorry, there was an error retrieving my help message. Contact the bot author.").block();
			return;
		}
		String helpMessage = resources.getProperty("helpPreString");
		for(Routine r : routines)
		{
			for(String t : r.getTriggers())
			{
				if(t.charAt(0)==':' && t.charAt(t.length()-1)==':') helpMessage += "\n" + t;
			}
		}
		helpMessage += resources.getProperty("helpPostString");
		PM.createMessage(helpMessage).block();
	}
	
	/**
	 * 
	 */
	private void handleColors(Set<Role> colorRoles, MessageChannel channel) {
		String colors = "Available colors are:\n";
		for(Role r : colorRoles)
		{
			colors += r.getName();
			colors += " ";
			colors += r.getColor().getRed();
			colors += " ";
			colors += r.getColor().getGreen();
			colors += " ";
			colors += r.getColor().getBlue();
			colors += "\n";
		}
		//remove the last newline
		channel.createMessage(colors.substring(0,colors.length()-1)).block();
	}
	
	/**
	 * 
	 */
	private void handleColorPermRole(String message, MessageChannel channel, Role colorPermRole) {
		//if role already exists, delete original and replace
		//else:
		colorPermRole = guild.getRoleById(Snowflake.of(message.substring(19))).block();
		channel.createMessage("Set the color permission role to be " + message.substring(19)).block();
	}
	
	private void handleAddColor(String message, Member sender, MessageChannel channel) {
		if(!permissionTest(sender, colorPermRole))
		{
			channel.createMessage("You don't have the permissions for that.").block();
			return;
		}
		Scanner sc = new Scanner(message.substring(11));
		String name = sc.next();
		final int red, green, blue; //default discord role color
		try
		{
			red = sc.nextInt();
			green = sc.nextInt();
			blue = sc.nextInt();
		}
		catch(Exception e)
		{
			channel.createMessage("Improper arguments.").block();
			sc.close();
			return;
		}
		sc.close();
		for(Role r : colorRoles)
		{
			if(r.getName().equals(name))
			{
				channel.createMessage("A color with that name already exists.").block();
				return;
			}
			if(r.getColor().getRed() == red && r.getColor().getGreen() == green && r.getColor().getBlue() == blue)
			{
				channel.createMessage("There is already a color with those values.").block();
				return;
			}
		}
		Role temp = guild.createRole(
				role -> role.setName(name).setColor(new Color(red,green,blue))//TODO: .setPosition() if the default is not last
		).block();
		colorRoles.add(temp);
		channel.createMessage(name + " added as a color.").block();
	}
	
	//return true if a role has at least one member
	private static boolean roleHasMember(Guild guild, Role role) {
		for(Member m : guild.getMembers().collectList().block()) {
			if(m.getRoleIds().contains(role.getId())) return true;
		}
		return false;
	}
	
	private void handleRemoveColor(String message, Member sender, MessageChannel channel) {
		if(!permissionTest(sender, colorPermRole))
		{
			channel.createMessage("You don't have the permissions for that.").block();
			return;
		}
		Role role = guild.getRoleById(Snowflake.of(message.substring(14))).block();
		if(roleHasMember(guild, role))
		{
			channel.createMessage("Cannot delete this role, at least one user is still assigned to it.").block();
			return;
		}
		role.delete();
		channel.createMessage("Color " + message.substring(14) + " deleted.").block();
		colorRoles.remove(role);
		//channel.createMessage("No color by the name " + message.substring(14) + "found.");
	}
	
	private void handleSetColor(String message, Member sender, MessageChannel channel) {
		if(!permissionTest(sender, colorPermRole))
		{
			channel.createMessage("You don't have the permissions for that.").block();
			return;
		}
		Role color = guild.getRoleById(Snowflake.of(message.substring(14))).block();
		if(sender.getRoleIds().contains(color.getId()))
		{
			sender.removeRole(color.getId());
			channel.createMessage("User color removed.").block();
		} else {
			sender.addRole(color.getId());
			channel.createMessage("User color set to " + color + ".").block();
		}
	}
	
	private static void handleTaunts(MessageCreateEvent event, Member sender) {
		PrivateChannel PM = sender.getPrivateChannel().block();
		try
		{
			File[] taunts = (new File("src/main/resources/taunts/")).listFiles();
			String temp = "Available taunts are:\n";
			for(File taunt : taunts)
			{
				temp += taunt.getName() + '\n';
			}
			//erase the last newline
			PM.createMessage(temp.substring(0,temp.length()-1)).block();
		}
		catch(Exception e)
		{
			PM.createMessage("Error occurred when retrieving list of sound files.").block();
		}
		event.getMessage().delete();
	}
	
	private void handleJoin(Member sender, MessageChannel channel) {
		audioPlayer = audioPlayerManager.createPlayer();
		audioPlayer.addListener(trackScheduler);
		
		//already joined check
		if(sender.getVoiceState().flatMap(voiceState -> voiceState.getChannel()).block().equals(client.getMemberById(guild.getId(), client.getSelfId().get()).flatMap(self -> self.getVoiceState().flatMap(voiceState -> voiceState.getChannel())).block())) {
			channel.createMessage("I've already joined your call.").block();
			return;
		} else {
			connection = sender.getVoiceState().block().getChannel().block().join(voiceChannel -> voiceChannel.setProvider(lavaPlayer)).block();
		}
	}
	
	private void handleLeave(MessageChannel channel) {
		if(connection == null) {
			channel.createMessage("I'm not currently in any voice channels.").block();
		} else {
			connection.disconnect();
			connection = null;
		}
	}
	
	private void handlePlay(String message, MessageChannel channel) {
		//ensure the audio source parameter is provided
		try {
			message.substring(7);
		} catch(IndexOutOfBoundsException e) {
			channel.createMessage("No argument provided. Provide a taunt name or link after the :play: command.").block();
			return;
		}
		
		String source;
		final boolean link;
		
		//determine whether the source is a file or link
		if(message.contains("http")) {
			source = message.substring(7);
			link = true;
		} else {
			source = "taunts/" + message.substring(7) + ".mp3";
			link = false;
		}
		
		audioPlayerManager.loadItem(source, new AudioLoadResultHandler() {
			@Override
			public void trackLoaded(AudioTrack track) {
				trackScheduler.addTrack(track);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				for(AudioTrack track : playlist.getTracks()) {
					trackScheduler.addTrack(track);
				}
			}

			@Override
			public void noMatches() {
				String response = "This looked like a ";
				if(link) {
					response += "link. No acceptable audio source was found.";
				} else {
					response += "file. No matching audio files for " + source + " were found.";
				}
				channel.createMessage(response).block();
			}
			
			@Override
			public void loadFailed(FriendlyException e) {
				channel.createMessage("Error: " + e.getMessage()).block();
			}
		});
	}
	
	private void handleSkip(MessageChannel channel) {
		if(audioPlayer.getPlayingTrack() != null) {
			audioPlayer.stopTrack();
			AudioTrack next = trackQueue.poll();
			if(next != null) {
				audioPlayer.playTrack(next);
			}
			
		} else {
			channel.createMessage("I'm not currently playing anything.").block();
		}
	}
	
	//usage: :remindme: HR:MN PM MonthName dd, yyyy true/false event; hour and day must be 2 digits, time zone must be US Central
	private void handleAddReminder(String message, Member sender, MessageChannel channel) {
		try {
			Reminder newReminder = parseReminder(message, sender.getId().asLong(), channel);
			//TODO: NULL CHECK!
			reminders.add(newReminder);
			storeReminder(newReminder);
		} catch(IllegalArgumentException e) {
			channel.createMessage(e.getMessage()).block();
		}
	}

	//usage: :addnew: :triggers: true/false users true/false response
	private void handleAddRoutine(String message, MessageChannel channel) {
		try {
			Routine newRoutine = parseRoutine(message);
			routines.add(newRoutine);
			storeRoutine(newRoutine);
		} catch(Exception e) {
			channel.createMessage(e.getMessage()).block();
		}
	}
	
	private static void sendGuildRequirementMessage(MessageChannel channel) {
		channel.createMessage("This message must be used inside a specific Discord guild.").block();
	}

	/*
		Handler check sequence:
		help message?
		view colors?
		set color role message from me?
		add color?
		removecolor?
		set color?
		move someone in queue channel?
		move all in queue channel?
		join voice channel?
		leave voice channel?
		select taunt?
		play taunt?
		create reminder?
		add a new trigger?
		check for trigger
		
		new sequence of most likely order:
		help > play taunt > join/leave voice channel > queue channel > colors > add new thing > general trigger check
	*/
	public void handle(MessageCreateEvent event)
	{
		try {
		//All of Discord's automated messages trigger this event, skip unless they are actually sent by a user
		if(!event.getMessage().getType().equals(Message.Type.DEFAULT)) return;
			
		//TODO: parameterize this check as a boolean to allow poonani to run functional tests on himself?
		//Ensure Poonani or other bots do not trigger him
		if(event.getMessage().getAuthor().get().isBot()) return;

		MessageChannel channel = event.getMessage().getChannel().block();
		boolean isDM = !(channel instanceof GuildMessageChannel);
		
		String message = event.getMessage().getContent().get();
		
		Member sender;
		if(isDM) {
			sender = null;
		} else {
			sender = event.getMember().get();
		}
		
//		//TODO: change to some user-defined role rather than the owner
//		boolean isAdmin = !isDM && permissionTest(sender, adminRole);
//		
//		if(message.equals(":help:") || message.equals(":?:"))
//		{
//			handleHelpMessage(event, sender, routines);
//			return;
//		}
//		
//		if(message.startsWith(":colors:"))
//		{
//			if(isDM) sendGuildRequirementMessage(channel);
//			else handleColors(colorRoles, channel);
//			return;
//		}
//		
//		if(message.startsWith(":setcolorpermrole:") && isAdmin)
//		{
//			if(isDM) sendGuildRequirementMessage(channel);
//			else handleColorPermRole(message, channel, colorPermRole);
//			return;
//		}
//		
//		//TODO: set admin role
//		//TODO: set color perm role
//		
//		//TODO: dupe check
//		if(message.startsWith(":addcolor:"))
//		{
//			if(isDM) sendGuildRequirementMessage(channel);
//			else handleAddColor(message, sender, channel);
//			return;
//		}
//		
//		if(message.startsWith(":removecolor:"))
//		{
//			if(isDM) sendGuildRequirementMessage(channel);
//			else handleRemoveColor(message, sender, (GuildMessageChannel) channel);
//			return;
//		}
//		
//		if(message.startsWith(":setcolor:"))
//		{
//			if(isDM) sendGuildRequirementMessage(channel);
//			else handleSetColor(message, sender, (GuildMessageChannel) channel);
//			return;
//		}
//		
//		if(message.startsWith(":taunts:"))
//		{
//			handleTaunts(event, sender);
//			return;
//		}
		
		//also check leave and skip when there's nothing playing
		if(message.startsWith(":join:"))
		{
			if(isDM) sendGuildRequirementMessage(channel);
			else handleJoin(sender, channel);
			return;
		}
		
		if(message.startsWith(":leave:"))
		{
			if(isDM) sendGuildRequirementMessage(channel);
			else handleLeave(channel);
			return;
		}
		
		if(message.startsWith(":play:"))
		{
			if(isDM) sendGuildRequirementMessage(channel);
			else handlePlay(message, channel);
			return;
		}
		
		if(message.startsWith(":skip:"))
		{
			if(isDM) sendGuildRequirementMessage(channel);
			else handleSkip(channel);
			return;
		}
//		
//		if(message.startsWith(":remindme:"))
//		{
//			handleAddReminder(message, sender, (GuildMessageChannel) channel);
//			return;
//		}
//		
//		//TODO: dupe check
//		if(message.startsWith(":addnew:") && isAdmin)
//		{
//			if(isDM) sendGuildRequirementMessage(channel);
//			else handleAddRoutine(message, (GuildMessageChannel) channel);
//			return;
//		}
//		
//		//the message has no known commands, check for routine triggers
//		for(Routine r : routines)
//		{
//			if(r.findTrigger(message, sender)) channel.createMessage(ttsMessage -> ttsMessage.setContent(r.getResponse()).setTts(r.getTTS())).block();
//		}
		}
		catch(Exception e) {
			//catch-all error handler, PMs me the details
			authorPM.createMessage("Error occurred in guild " + guild.getName() + " with message: " + e.getMessage()).block();
		}
	}
}