package dakota.poonani;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ListIterator;
import java.util.Scanner;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalSeekableInputStream;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.EventDispatcher;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.data.stored.PresenceBean;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.presence.Presence;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/***********************
420/Dakota's Discord Bot

Changelog

1.0:
	Bot debut.

1.2:
	Added changelist.
	Added to do area.
	
	Cleaned up code.
		Added message and sender variables to improve readability.
		Organized into explicit and automatic commands.
		Condensed phrase finding routine into a new function, phraseExists.
		Refactored logic of shiro is old function.

	Added new routines.
		Created an imgur album to host:
			new images/gifs
			unused/small emojis
			oblivion guard/gg images
		Added functions for the new images/gifs above.
		Added :help: command to list current features.

1.3:
	Changed phraseExists:
		Added an extra initial includes() check to improve performance.
		Changed logic from:
			beginning + space | space + phrase | contains phrase
		to:
			beginning + space | space + phrase (+ punctuation) + space | end (+ punctuation)
		Added atEnd function to implement the "end (+ punctuation)" portion of phraseExists.
	Added the :^), ochinchin, and allmight functions.
		
1.4a:
	Formatted the help statement nicely.
	Added more images/gifs.
1.4b:
	Fixed bug in atEnd function.
	Added Man of Culture and Thumbs Up.

1.5:
	Changed atBeginning to also recognize punctuation after the phrase.
	Changed atMiddle to recognize newlines preceding the phrase in addition to whitespace.
	
2.0
	Java port.
	Implemented system to allow adding new routines through discord itself.
		Routine information is stored locally in text files.

2.1
	Added a routine for Wednesdays.
	Prevented the assignment of special commands as triggers.
	
2.2
	Added the system to be notified and move users into the private voice channel.
	Cleaned up code, including rearranging all handle check functions to be in order of most frequent to least, with the exception of trigger checking.
	Changed the help message to be sent as a PM and forwent the formatting.
	Cleaned up :addnew:, removed the need for boolean states by implementing it in one line command.
	Also removed the retrieve boolean state by calling retrieve directly from the main thread.
	Added the groundwork for the reminder system.

2.3
	Finished implementing and testing the reminder system.
	Discovered the proper method for storing information in files, massive code cleanup concerning storage/retrieval of routines/reminders.

2.4
	Changed isMe, now runs more like isOwner
	Added ability to set a role that allows one to change their color, using color roles (roles for the sole purpose of coloring one's username)
	
2.5
	Added the ability for anyone with color permissions to add (and remove if non-used) a new color with name and RGB value arguments.
	Added a command to view all current colors.
	Added all color-related information to the help command.
	
2.6
	Added the sound playing system, including join/leave channel commands, and ability to play any sound file (mp3) in the taunts folder I created
	
3.0
	Total refactor to conform to discord4j 3.0.
	Reorganized to move everything to Main and keep Handler as much of a static method class as possible.
	Reworked routines/reminders/roles to be stored within discord rather than sent to file.
	
Backlog TODO:
add log messages throughout application to determine why/when poonani becomes unresponsive
make help and taunts messages less ugly (add to resource files)
put admin commands in help, label as such
change properties of triggers through discord (should be covered by moving routines to pinned message format)
*test* entering newlines as part of routine/reminder arguments
add boolean after users and before response indicating users are to be included or excluded
	use to implement shiro's old
remove/edit a routine
add lots of tests
regex checking on commands with parameters before grabbing the params with substring(x)
queue function for audio player
time specific reminders (1 day, 2 hours, 10 minutes) in addition to the usual date reminders (12/23 12:00 PM)
catch all exceptions, log as logger.error()
better error handling for routine/reminder creation
if major initialization error occurs (leaves admin role null) send message to owner that something went wrong and to send error to author, then leave guild

* 3.0 TODO:
* Secret role (parameterized name) for routines/reminders that allows permission to see a channel with pinned messages containing routine/reminder/role information
* 		Store the information in discord instead of sending it to file, also modifiable directly by users
* 		Should additionally create bot_test channel if it doesn't exist (ideally, parameterize name of this channel)
* Extend reminder to have mention me boolean. Implement wednesday using a reminder
* 		Add a way to remove reminders before they're triggered
* extend reminder to allow recurring
* Add override for remove color to ignore users still using it (requires admin role)
* Note in help message about settings > appearance > developer mode on to get IDs for commands
* figure out how mono errors work (e.g. given ID does not match any of the object being retrieved)
* delete role accidentally created
* role for creating routines (default admin) and role for creating reminders (default everyone)
* change constructor and getter/setter parameters to have the same name, use this.x = x notation
* 	fix javadocs in general, @ params and @ returns all with proper formatting and names
* 
* rework flow of adding bot - no functionality until settings (storage channel [e.g. bot_test], admin role [at least manage server], user Mention settings) have been set
* single pinned message in storage channel containing Snowflakes of all other messages
***********************/

public class Main {
	
	/*
	 * Fields
	 */
	
	private static Logger logger = LoggerFactory.getLogger(Main.class);
	
	private static LocalDateTime now;
	private static DiscordClient client;
	
	//Keep GuildHandlers sorted by the ID of the guild
	private static TreeMap<Long, GuildHandler> handlers = new TreeMap<Long, GuildHandler>();
	
	private static ReminderThread reminderThread = new ReminderThread();
	private static class ReminderThread extends Thread {
		public boolean stop = false;
		public void run() {
			while(!stop) {
				try {
					sleep(handleReminders() * 1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private final static class JarAudioResourceManager extends LocalAudioSourceManager {
		//essentially LocalAudioSourceManager, except if attempting to directly create a File fails
		//loadItem() will use getResource instead, to enable the use of resource files inside jar files
		@Override
		public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
			AudioItem item = super.loadItem(manager, reference);
			if(item == null) {
				File file = new File(getClass().getClassLoader().getResource(reference.identifier).getFile());
				if(file.exists() && file.isFile() && file.canRead()) {
					return handleLoadResult(detectContainerForFile(reference, file));
				} else {
					System.out.println("File " + file.getName() + " didn't exist");
					return null;
				}
			} else {
				return item;
			}
		}
		
		private MediaContainerDetectionResult detectContainerForFile(AudioReference reference, File file) {
		    try (LocalSeekableInputStream inputStream = new LocalSeekableInputStream(file)) {
		        int lastDotIndex = file.getName().lastIndexOf('.');
		        String fileExtension = lastDotIndex >= 0 ? file.getName().substring(lastDotIndex + 1) : null;

		        return new MediaContainerDetection(containerRegistry, reference, inputStream,
		            MediaContainerHints.from(null, fileExtension)).detectContainer();
		      } catch (IOException e) {
		        throw new FriendlyException("Failed to open file for reading.", SUSPICIOUS, e);
		      }
		    }
	}
	
	/*
	 * Methods
	 */
	
	/**
	 * Initializes the bot user, creates event listeners, reads information from file, then continually checks reminders and events every minute
	 * @param args Command line args (unused)
	 */
	public static void main(String[] args)
	{
		logger.info("Starting instance of Poonani");
		
		//Initialize user, login with custom status
		PresenceBean customStatus = new PresenceBean();
		customStatus.setStatus("Back from the dead!");
		//customStatus.setStatus("Under construction!");
		client = new DiscordClientBuilder(args[0])
				.setInitialPresence(new Presence(customStatus))
				//.setRetryOptions(new RetryOptions(Duration.ZERO, Duration.ZERO, Integer.MAX_VALUE, ???))
				.build();
		logger.info("Created the client");
		
		//Initialize audio functions
		AudioPlayerManager audioPlayerManager = new DefaultAudioPlayerManager();
		audioPlayerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
		AudioSourceManagers.registerRemoteSources(audioPlayerManager);
		AudioSourceManagers.registerLocalSource(audioPlayerManager);
		//audioPlayerManager.registerSourceManager(new JarAudioResourceManager());
		
		//Initialize GuildHandler
		GuildHandler.client = client;
		GuildHandler.audioPlayerManager = audioPlayerManager;
		
		//Register event handlers
		EventDispatcher dispatcher = client.getEventDispatcher();
		dispatcher.on(GuildCreateEvent.class).subscribe(event -> initializeGuild(event));
		dispatcher.on(MessageCreateEvent.class).subscribe(event -> handlers.get(event.getGuild().block().getId().asLong()).handle(event));
		dispatcher.on(MemberJoinEvent.class).subscribe(event -> handlers.get(event.getGuild().block().getId().asLong()).handle(event));
		
		//Login and wait for it to be connected
		logger.info("Attempting to log in");
		client.login().subscribe();
		while(!client.isConnected()) {
			sleep(5000);
			System.out.println("Still initializing.");
		}
		
//		reminderThread.start();
		
		//server method of properly stopping the Poonani instance
		Scanner scanner = new Scanner(System.in);
		System.out.println("Done initializing. Enter quit to cease execution.");
		while(!scanner.nextLine().equals("quit")) {}
		scanner.close();
		logger.info("Quit command received. Waiting for a reminder check cycle to finish");
		reminderThread.stop = true;
		while(reminderThread.isAlive()) {}
		logger.info("Logging out");
		client.logout().block();
	}
	
	private static void initializeGuild(GuildCreateEvent event) {
		//GuildCreateEvent is emitted on reconnections, so only add guilds if they are genuinely new connections (or the bot is starting up)
		if(handlers.containsKey(event.getGuild().getId().asLong())) return;
		
		handlers.put(event.getGuild().getId().asLong(), new GuildHandler(event.getGuild()));
	}
	
	private static void sleep(long duration) {
		try { Thread.sleep(duration); }
		catch(Exception e)
		{
			System.out.println(e.getClass().getSimpleName() + ": " + e.getMessage() + "\nStack trace:");
			e.printStackTrace();
		}
	}

	//check if any reminders are due. additionally, calculate and return the amount of time until the soonest upcoming reminder
	//default behavior (if there are no other reminders to set the wait time) is to check every minute
	private static long handleReminders() {
		long minutesToWait = 1;
		now = LocalDateTime.now();
		//check the list of reminders for each guild
		for(GuildHandler handler : handlers.values()) {
			//this method of iteration is used so that elements can be deleted mid-iteration
			ListIterator<Reminder> iter = handler.getReminders().listIterator();
			while(iter.hasNext())
			{
				Reminder r = iter.next();
				if(now.isAfter(r.getTime()))
				{
					client.getChannelById(r.getChannelSnowflake()).subscribe(channel -> {
						String event = r.getEvent();
						if(r.getMention()) {
							event = client.getUserById(r.getUserSnowflake()).block().getUsername() + ", " + event;
						}
						((MessageChannel) channel).createMessage(event);
					});
					iter.remove();
				} else {
					long minutesToNext = ChronoUnit.MINUTES.between(now, r.getTime());
					if(minutesToNext > minutesToWait) {
						minutesToWait = minutesToNext;
					}
				}
			}
		}
		return minutesToWait;
	}
}