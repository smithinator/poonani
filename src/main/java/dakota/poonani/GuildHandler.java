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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
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
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;

import discord4j.core.DiscordClient;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.GuildMessageChannel;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.TextChannel;
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
	
	//Guild-specific info
	private Guild guild;
	private List<Routine> routines;
	private List<Reminder> reminders;
	private Set<Role> colorRoles;
	private Role colorPermRole;
	private Role adminRole;
	
	//New user
	private boolean welcomeNewUser = true;
	private boolean newUserMention = true;
	private String newUserMessage = "https://www.youtube.com/watch?v=Za2PJnCAkUA";
	
	//Storage
	private TextChannel storageChannel;
	private static final String STORAGE_CHANNEL = "poonaniStorage";
	private static final String STORAGE_CHANNEL_DESC = "Where Poonani Tsunami stores information";
	private static final String STORAGE_CHANNEL_CREATE_REASON = "Creating a channel to store information for this server (hidden by default, can be changed)";
	
	//Audio
	public static AudioPlayerManager audioPlayerManager;
	private AudioPlayer audioPlayer;
	private LavaPlayerAudioProvider lavaPlayer = new LavaPlayerAudioProvider();
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
		logWithGuildId(LoggerLevel.DEBUG, "Completed storage/admin initialization, beginning routine initialization");
		retrieveRoutines();
		logWithGuildId(LoggerLevel.DEBUG, "Completed routine initialization, beginning reminder initialization");
		retrieveReminders();
		logWithGuildId(LoggerLevel.DEBUG, "Completed reminder initialization, beginning color role initialization");
		retrieveColorRoles();
		logWithGuildId(LoggerLevel.DEBUG, "Completed color role initialization, beginning color perm role initialization");
		retrieveColorPermRole();
		logWithGuildId(LoggerLevel.DEBUG, "Completed color perm role initialization, beginning user join message initialization");
		retrieveUserJoinMessage();
		logWithGuildId(LoggerLevel.DEBUG, "Completed user join message initialization, successfully initialized handler");
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
	 * Methods
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
	
	/**
	 * These two processes are handled in the same method because they are interdependent. The execution logic is:
	 * if storage channel is nonexistent (new guild) -> create default admin role if it doesn't already exist, then create storage channel (with perms for new admin role)
	 * else read storage channel, then read admin role from storage channel
	 * If the storage channel does exist, then all the pinned messages are searched to find the stored admin role. The admin role is in the format:
	 * ADMIN ROLE:
	 * [role id]
	 */
	private void retrieveStorageChannelAndAdminRole() {
		guild.getChannels().filter(channel -> channel instanceof TextChannel).filter(channel -> channel.getName().equals(STORAGE_CHANNEL)).collectList().subscribe(channels -> {
			if(channels.isEmpty()) {
				//new guild, create default admin role and assign to owner
				logWithGuildId(LoggerLevel.DEBUG, "Storage channel not found, creating a default admin role and then the channel");
				
				//Handle the edge case that the default admin role exists without the storage channel
				List<Role> adminRoles = guild.getRoles().filter(role -> role.getName().equals("Poonani Admin")).collectList().block();
				if(!adminRoles.isEmpty()) {
					adminRole = adminRoles.get(0);
					logWithGuildId(LoggerLevel.WARN, "Storage channel wasn't found but the admin role was. Guild data may be in bad state. Using the existing role");
				} else {
					//TODO: look for lowest role with "Manage Server" perm, use this as 
					adminRole = guild.createRole(spec -> spec.setName("Poonani Admin").setReason("No admin role for Poonani existed yet, created a default")).block();
					guild.getOwner().subscribe(owner -> owner.addRole(adminRole.getId()));
					logWithGuildId(LoggerLevel.DEBUG, "Created initial default admin role and assigned to guild owner successfully");
					//create a new channel that is visible to adminRole and Poonani but not visible to @everyone role
					Set<PermissionOverwrite> perms = new HashSet<PermissionOverwrite>();
					perms.add(PermissionOverwrite.forRole(guild.getEveryoneRole().block().getId(), PermissionSet.none(), PermissionSet.of(Permission.VIEW_CHANNEL)));
					perms.add(PermissionOverwrite.forRole(adminRole.getId(), PermissionSet.of(Permission.VIEW_CHANNEL), PermissionSet.none()));
					perms.add(PermissionOverwrite.forMember(client.getSelf().block().asMember(guild.getId()).block().getId(), PermissionSet.of(Permission.VIEW_CHANNEL), PermissionSet.none()));
					storageChannel = guild.createTextChannel(spec -> spec.setName(STORAGE_CHANNEL).setTopic(STORAGE_CHANNEL_DESC).setPermissionOverwrites(perms).setReason(STORAGE_CHANNEL_CREATE_REASON)).block();
					logWithGuildId(LoggerLevel.DEBUG, "Storage channel created successfully");
				}
			} else {
				logWithGuildId(LoggerLevel.DEBUG, "Storage channel found successfully");
				storageChannel = (TextChannel) channels.get(0);
				
				//retrieve admin role
				try {
					storageChannel.getPinnedMessages().filter(message -> message.getContent().filter(content -> content.startsWith("ADMIN ROLE:\n")).isPresent()).subscribe(message -> {
						//get everything after the first newline, since in stored form the message should be COLOR ROLE:\n[actual message]
						adminRole = client.getRoleById(guild.getId(), Snowflake.of(Long.valueOf(message.getContent().get().split("\n")[1]))).block();
						logWithGuildId(LoggerLevel.DEBUG, "Found the admin role and stored it successfully");
					});
					if(adminRole == null) {
						//TODO: edge case of storage channel without admin role
					}
				} catch(Exception e) {
					logWithGuildId(LoggerLevel.ERROR, "Admin Role message retrieval failed:\n" + e.getMessage());
				}
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
				reminders.add(parseReminder(content[3], Long.valueOf(content[1]), Long.valueOf(content[2])));
				logWithGuildId(LoggerLevel.DEBUG, "Found a reminder and successfully stored it");
			} catch(IllegalArgumentException e) {
				logWithGuildId(LoggerLevel.ERROR, "Reminder message failed to parse:\n" + e.getMessage());
			}
		});
	}
	
	/**
	 * At initialization, searches all the pinned messages of the storage channel to find stored color roles. Color roles are in the format:
	 * COLOR ROLE:
	 * [role id]
	 */
	private void retrieveColorRoles() {
		colorRoles = new HashSet<Role>();
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
	 * At initialization, searches all the pinned messages of the storage channel to find the stored color permission role. The color permission role is in the format:
	 * COLOR PERM ROLE:
	 * [role id]
	 */
	private void retrieveColorPermRole() {
		storageChannel.getPinnedMessages().filter(message -> message.getContent().filter(content -> content.startsWith("COLOR PERM ROLE:\n")).isPresent()).subscribe(message -> {
			try {
				//get everything after the first newline, since in stored form the message should be COLOR PERM ROLE:\n[actual message]
				colorPermRole = client.getRoleById(guild.getId(), Snowflake.of(Long.valueOf(message.getContent().get().split("\n")[1]))).block();
				logWithGuildId(LoggerLevel.DEBUG, "Found the color perm role and stored it successfully");
			} catch(IllegalArgumentException e) {
				logWithGuildId(LoggerLevel.ERROR, "Color Perm Role message retrieval failed:\n" + e.getMessage());
			}
		});
		//no role found. must be called before retrieveAdminRole, as the default/new guild behavior is to set this role to the admin role
		if(colorPermRole == null) {
			colorPermRole = adminRole;
		}
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
	
	/**
	 * Prints a message to the system channel welcoming a new user, if the feature is enabled.
	 * May or may not mention the user depending on the mention setting.
	 * @param event
	 */
	public void handle(MemberJoinEvent event)
	{
		if(!welcomeNewUser) return;
		Member newUser = event.getMember();
		if(newUserMention) {
			guild.getSystemChannel().block().createMessage(newUserMessage);
		} else {
			guild.getSystemChannel().block().createMessage(newUser.getMention() + " " + newUserMessage);
		}
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
		storageChannel.createMessage(routineMessage);
	}
	
	//usage: :remindme: HR:MN PM MonthName dd, yyyy true/false event; hour and day must be 2 digits, time zone must be US Central
	private void storeReminder(Reminder reminder)
	{
		//perm checks below in handleAdd
		storageChannel.createMessage("REMINDER:\n:remindme: "
				+ DateTimeFormatter.ofPattern("KK:mm a MMMM dd, yyyy").format(reminder.getTime()) + " "
				+ String.valueOf(reminder.getMention())
				+ reminder.getEvent());
	}
	
	private void storeColorRole(Role role)
	{
		//after dupe and perm checks, add role to poonani channel
		storageChannel.createMessage("COLOR ROLE:\n" + role.getId().asLong());
	}
	
	private void storeColorPermRole(Role role) {
		storageChannel.createMessage("COLOR PERM ROLE:\n" + role.getId().asLong());
	}
	
	private void storeAdminRole(Role role) {
		storageChannel.createMessage("ADMIN ROLE:\n" + role.getId().asLong());
	}
	
	private static boolean permissionTest(Member member, Role permRole)
	{
		return member.getHighestRole().block().getPosition().block() >= permRole.getPosition().block();
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
	
	private static Reminder parseReminder(String message, Long userId, Long channelId) {
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
			}
			else throw new DataFormatException();
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("KK:mm a MMMM dd, yyyy");
			Reminder newReminder = new Reminder(message.substring(matcher.start() + 5), LocalDateTime.from(formatter.parse(date)), true, userId, channelId);
			throw new IllegalArgumentException("Okay, I'll remind you about \"" + newReminder.getEvent() + "\" at " + date);
		}
		catch(Exception e) {
			throw new IllegalArgumentException("Sorry, I couldn't understand that date and time. The usage is :remindme: HR:MN PM MonthName dd, yyyy true/false event; hour and day must be 2 digits.");
		}
	}
	
	/*
	 * Handler methods
	 */
	
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
			PM.createMessage("Sorry, there was an error retrieving my help message. Contact the bot author.");
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
		PM.createMessage(helpMessage);
	}
	
	/**
	 * 
	 */
	private static void handleColors(Set<Role> colorRoles, MessageChannel channel) {
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
		channel.createMessage(colors.substring(0,colors.length()-1));
	}
	
	/**
	 * 
	 */
	private static void handleColorPermRole(String message, GuildMessageChannel channel, Role colorPermRole) {
		//if role already exists, delete original and replace
		//else:
		colorPermRole = channel.getGuild().block().getRoleById(Snowflake.of(message.substring(19))).block();
		channel.createMessage("Set the color permission role to be " + message.substring(19));
	}
	
	private void handleAddColor(String message, Member sender, GuildMessageChannel channel) {
		if(!permissionTest(sender, colorPermRole))
		{
			channel.createMessage("You don't have the permissions for that.");
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
			channel.createMessage("Improper arguments.");
			sc.close();
			return;
		}
		sc.close();
		for(Role r : colorRoles)
		{
			if(r.getName().equals(name))
			{
				channel.createMessage("A color with that name already exists.");
				return;
			}
			if(r.getColor().getRed() == red && r.getColor().getGreen() == green && r.getColor().getBlue() == blue)
			{
				channel.createMessage("There is already a color with those values.");
				return;
			}
		}
		Role temp = channel.getGuild().block().createRole(
				role -> role.setName(name).setColor(new Color(red,green,blue))//TODO: .setPosition() if the default is not last
		).block();
		colorRoles.add(temp);
		channel.createMessage(name + " added as a color.");
	}
	
	//return true if a role has at least one member
	private static boolean roleHasMember(Guild guild, Role role) {
		for(Member m : guild.getMembers().collectList().block()) {
			if(m.getRoleIds().contains(role.getId())) return true;
		}
		return false;
	}
	
	private void handleRemoveColor(String message, Member sender, GuildMessageChannel channel) {
		if(!permissionTest(sender, colorPermRole))
		{
			channel.createMessage("You don't have the permissions for that.");
			return;
		}
		Role role = channel.getGuild().block().getRoleById(Snowflake.of(message.substring(14))).block();
		if(roleHasMember(channel.getGuild().block(), role))
		{
			channel.createMessage("Cannot delete this role, at least one user is still assigned to it.");
			return;
		}
		role.delete();
		channel.createMessage("Color " + message.substring(14) + " deleted.");
		colorRoles.remove(role);
		//channel.createMessage("No color by the name " + message.substring(14) + "found.");
	}
	
	private void handleSetColor(String message, Member sender, GuildMessageChannel channel) {
		if(!permissionTest(sender, colorPermRole))
		{
			channel.createMessage("You don't have the permissions for that.");
			return;
		}
		Role color = channel.getGuild().block().getRoleById(Snowflake.of(message.substring(14))).block();
		if(sender.getRoleIds().contains(color.getId()))
		{
			sender.removeRole(color.getId());
			channel.createMessage("User color removed.");
		} else {
			sender.addRole(color.getId());
			channel.createMessage("User color set to " + color + ".");
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
			PM.createMessage(temp.substring(0,temp.length()-1));
		}
		catch(Exception e)
		{
			PM.createMessage("Error occurred when retrieving list of sound files.");
		}
		event.getMessage().delete();
	}
	
	private void handleJoin(Member sender) {
		audioPlayer = audioPlayerManager.createPlayer();
		
		connection = sender.getVoiceState().block().getChannel().block().join(channel -> channel.setProvider(lavaPlayer)).block();
	}
	
	private void handleLeave(GuildMessageChannel channel) {
		if(connection == null) {
			channel.createMessage("I'm not currently in any voice channels.");
		} else {
			connection.disconnect();
			connection = null;
		}
	}
	
	private void handlePlay(String message, MessageChannel channel) {
		String taunt = "";
		try
		{
			final String fileName = message.substring(7);
			String filePath = "src/main/resources/taunts/";
			List<Path> taunts = Files.list(Paths.get(filePath))
				.filter(path -> path.getFileName().toString().startsWith(fileName))
				.collect(Collectors.toList());
			taunt = filePath + taunts.get(0).getFileName().toString();
		}
		catch(Exception e)
		{
			channel.createMessage("Improper arguments or taunt couldn't be found.");
			return;
		}
		audioPlayerManager.loadItem(taunt, new AudioLoadResultHandler() {
			@Override
			public void loadFailed(FriendlyException arg0) {
				channel.createMessage("Couldn't load the taunt. Maybe it doesn't exist or was typed incorrectly.");
			}

			@Override
			public void noMatches() {
				channel.createMessage("Couldn't load the taunt. Maybe it doesn't exist or was typed incorrectly.");
			}

			@Override
			public void playlistLoaded(AudioPlaylist arg0) {
			}

			@Override
			public void trackLoaded(AudioTrack track) {
				audioPlayer.playTrack(track);
			}
		});
	}
	
	/*
	private void handleQueue(String message, MessageChannel channel) {
		
	}
	 */
	
	//usage: :remindme: HR:MN PM MonthName dd, yyyy true/false event; hour and day must be 2 digits, time zone must be US Central
	private void handleAddReminder(String message, Member sender, GuildMessageChannel channel) {
		try {
			Reminder newReminder = parseReminder(message, sender.getId().asLong(), channel.getId().asLong());
			reminders.add(newReminder);
			storeReminder(newReminder);
		} catch(IllegalArgumentException e) {
			channel.createMessage(e.getMessage());
		}
	}

	//usage: :addnew: :triggers: true/false users true/false response
	private void handleAddRoutine(String message, GuildMessageChannel channel) {
		try {
			Routine newRoutine = parseRoutine(message);
			routines.add(newRoutine);
			storeRoutine(newRoutine);
		} catch(Exception e) {
			channel.createMessage(e.getMessage());
		}
	}
	
	private static void sendGuildRequirementMessage(MessageChannel channel) {
		channel.createMessage("This message must be used inside a specific Discord guild.");
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
		//TODO: parameterize this check as a boolean to allow poonani to run functional tests on himself?
		if(event.getMember().get().isBot()) return;

		String message = event.getMessage().getContent().get();
		Member sender = event.getMember().get();
		MessageChannel channel = event.getMessage().getChannel().block();
		boolean isDM = !(channel instanceof GuildMessageChannel);
		//TODO: change to some user-defined role rather than the owner
		boolean isAdmin = !isDM && permissionTest(sender, adminRole);
		
		if(message.equals(":help:") || message.equals(":?:"))
		{
			handleHelpMessage(event, sender, routines);
			return;
		}
		
		if(message.startsWith(":colors:"))
		{
			if(isDM) sendGuildRequirementMessage(channel);
			else handleColors(colorRoles, channel);
			return;
		}
		
		if(message.startsWith(":setcolorpermrole:") && isAdmin)
		{
			if(isDM) sendGuildRequirementMessage(channel);
			else handleColorPermRole(message, (GuildMessageChannel) channel, colorPermRole);
			return;
		}
		
		if(message.startsWith(":addcolor:"))
		{
			if(isDM) sendGuildRequirementMessage(channel);
			else handleAddColor(message, sender, (GuildMessageChannel) channel);
			return;
		}
		
		if(message.startsWith(":removecolor:"))
		{
			if(isDM) sendGuildRequirementMessage(channel);
			else handleRemoveColor(message, sender, (GuildMessageChannel) channel);
			return;
		}
		
		if(message.startsWith(":setcolor:"))
		{
			if(isDM) sendGuildRequirementMessage(channel);
			else handleSetColor(message, sender, (GuildMessageChannel) channel);
			return;
		}
		
		if(message.startsWith(":taunts:"))
		{
			handleTaunts(event, sender);
			return;
		}
		
		if(message.startsWith(":join:"))
		{
			if(isDM) sendGuildRequirementMessage(channel);
			else handleJoin(sender);
			return;
		}
		
		if(message.startsWith(":leave:"))
		{
			if(isDM) sendGuildRequirementMessage(channel);
			else handleLeave((GuildMessageChannel) channel);
			return;
		}
		
		if(message.startsWith(":play:"))
		{
			if(isDM) sendGuildRequirementMessage(channel);
			else handlePlay(message, channel);
			return;
		}
		
		if(message.startsWith(":remindme:"))
		{
			handleAddReminder(message, sender, (GuildMessageChannel) channel);
			return;
		}
		
		if(message.startsWith(":addnew:") && isAdmin)
		{
			if(isDM) sendGuildRequirementMessage(channel);
			else handleAddRoutine(message, (GuildMessageChannel) channel);
			return;
		}
		
		//the message has no known commands, check for routine triggers
		for(Routine r : routines)
		{
			if(r.findTrigger(message, sender)) channel.createMessage(ttsMessage -> ttsMessage.setContent(r.getResponse()).setTts(r.getTTS()));
		}
	}
}