/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.sedmelluq.discord.lavaplayer.filter.equalizer.EqualizerFactory;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import com.jagrosh.jmusicbot.queue.FairQueue;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack;
import java.nio.ByteBuffer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class AudioHandler extends AudioEventAdapter implements AudioSendHandler 
{
    public final static String PLAY_EMOJI  = "\u25B6"; // ▶
    public final static String PAUSE_EMOJI = "\u23F8"; // ⏸
    public final static String STOP_EMOJI  = "\u23F9"; // ⏹
    
    private final FairQueue<QueuedTrack> queue = new FairQueue<>();
    private final List<AudioTrack> defaultQueue = new LinkedList<>();
    private final Set<String> votes = new HashSet<>();
    
    private final PlayerManager manager;
    private final EqualizerFactory equalizer;
    private final AudioPlayer audioPlayer;
    private final long guildId;
    
    private AudioFrame lastFrame;

    // Now playing
    private String currentTitle;
    private EmbedBuilder currentEB;
    private boolean nowPlayingUpdated;
    private int prevVolume;
    private String prevNext;

    protected AudioHandler(PlayerManager manager, Guild guild, AudioPlayer player) {
        this.manager = manager;
        this.equalizer = new EqualizerFactory();
        this.audioPlayer = player;
        this.audioPlayer.setFilterFactory(equalizer);
        this.audioPlayer.setFrameBufferDuration(500); 
        this.guildId = guild.getIdLong();
        this.currentTitle = null;
        this.currentEB = new EmbedBuilder();
        this.nowPlayingUpdated = false;
        this.prevVolume = -1;
        this.prevNext = "";
    }

    public int addTrackToFront(QueuedTrack qtrack) {
        if(audioPlayer.getPlayingTrack()==null)
        {
            audioPlayer.playTrack(qtrack.getTrack());
            return -1;
        }
        else
        {
            queue.addAt(0, qtrack);
            return 0;
        }
    }
    
    public int addTrack(QueuedTrack qtrack)
    {
        if(audioPlayer.getPlayingTrack()==null)
        {
            audioPlayer.playTrack(qtrack.getTrack());
            return -1;
        }
        else
            return queue.add(qtrack);
    }
    
    public FairQueue<QueuedTrack> getQueue()
    {
        return queue;
    }
    
    public void stopAndClear()
    {
        queue.clear();
        defaultQueue.clear();
        audioPlayer.stopTrack();
        //current = null;
    }
    
    public boolean isMusicPlaying(JDA jda)
    {
        return guild(jda).getSelfMember().getVoiceState().inVoiceChannel() && audioPlayer.getPlayingTrack()!=null;
    }
    
    public Set<String> getVotes()
    {
        return votes;
    }
        
    public EqualizerFactory getEqualizer()
    {
        return equalizer;
    }

    public void resetEQ()
    {
        audioPlayer.setFilterFactory(null);
        audioPlayer.setFilterFactory(equalizer);
    }
    
    public AudioPlayer getPlayer()
    {
        return audioPlayer;
    }
    
    public RequestMetadata getRequestMetadata()
    {
        if(audioPlayer.getPlayingTrack() == null)
            return RequestMetadata.EMPTY;
        RequestMetadata rm = audioPlayer.getPlayingTrack().getUserData(RequestMetadata.class);
        return rm == null ? RequestMetadata.EMPTY : rm;
    }
    
    public boolean playFromDefault()
    {
        if(!defaultQueue.isEmpty())
        {
            audioPlayer.playTrack(defaultQueue.remove(0));
            return true;
        }
        Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
        if(settings==null || settings.getDefaultPlaylist()==null)
            return false;
        
        Playlist pl = manager.getBot().getPlaylistLoader().getPlaylist(settings.getDefaultPlaylist());
        if(pl==null || pl.getItems().isEmpty())
            return false;
        pl.loadTracks(manager, (at) -> 
        {
            if(audioPlayer.getPlayingTrack()==null)
                audioPlayer.playTrack(at);
            else
                defaultQueue.add(at);
        }, () -> 
        {
            if(pl.getTracks().isEmpty() && !manager.getBot().getConfig().getStay())
                manager.getBot().closeAudioConnection(guildId);
        });
        return true;
    }
    
    // Audio Events
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) 
    {
        RepeatMode repeatMode = manager.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();
        // if the track ended normally, and we're in repeat mode, re-add it to the queue
        if(endReason==AudioTrackEndReason.FINISHED && repeatMode != RepeatMode.OFF)
        {
            QueuedTrack clone = new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class));
            if(repeatMode == RepeatMode.ALL)
                queue.add(clone);
            else
                queue.addAt(0, clone);
        }
        
        if(queue.isEmpty())
        {
            if(!playFromDefault())
            {
                manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, null, this);
                if(!manager.getBot().getConfig().getStay())
                    manager.getBot().closeAudioConnection(guildId);
                // unpause, in the case when the player was paused and the track has been skipped.
                // this is to prevent the player being paused next time it's being used.
                player.setPaused(false);
            }
        }
        else
        {
            QueuedTrack qt = queue.pull();
            player.playTrack(qt.getTrack());
        }
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) 
    {
        votes.clear();
        manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, track, this);
    }

    public boolean getNowPlayingUpdated() {
        return nowPlayingUpdated;
    }
    
    // Formatting
    public Message getNowPlaying(JDA jda) {
        // Check if music is playing
        if(!isMusicPlaying(jda)) {
            return null;
        }

        Guild guild = guild(jda);
        AudioTrack track = audioPlayer.getPlayingTrack();
        MessageBuilder mb = new MessageBuilder();
        
        nowPlayingUpdated = true;

        // Check if track is different to avoid re-downloading the thumbnail
        if (!track.getInfo().title.equals(currentTitle)) {
            // Cache EmbedBuilder
            currentEB = new EmbedBuilder();
            currentEB.setColor(guild.getSelfMember().getColor());

            // Cache title
            currentTitle = track.getInfo().title;

            // Cache Thumbnail
            if(track instanceof YoutubeAudioTrack) {
                currentEB.setImage("https://img.youtube.com/vi/"+track.getIdentifier()+"/mqdefault.jpg");
            }
        }

        // Do not update if we are paused
        boolean paused = audioPlayer.isPaused();
        if (paused) {
            nowPlayingUpdated = false;
        }

        // Update if the volume has changed
        int curVolume = audioPlayer.getVolume();
        if (prevVolume != curVolume) {
            prevVolume = curVolume;
            nowPlayingUpdated = true;
        }

        // Update if up-next has changed
        String curNext = "";
        if(!queue.isEmpty()) {
            curNext = queue.get(0).getTrack().getInfo().title;
        }

        if (prevNext != curNext) {
            prevNext = curNext;
            nowPlayingUpdated = true;
        }

        // Check if we need to update
        if (!nowPlayingUpdated) {
            return mb.setEmbeds(currentEB.build()).build();
        }

        // Clear all fields
        currentEB.clearFields();

        // Currently Playing
        try {
            currentEB.addField("Currently " + (paused ? "Paused" : "Playing"), "[" + track.getInfo().title + "](" + track.getInfo().uri  + ")", false);
        } catch(Exception e) {
            currentEB.addField("Currently " + (paused ? "Paused" : "Playing"), track.getInfo().title, false);
        }

        // Artist
        if(track.getInfo().author != null && !track.getInfo().author.isEmpty()) {
            currentEB.addField("By", track.getInfo().author, false);
        }
        
        // Requested By
        RequestMetadata rm = getRequestMetadata();
        if(rm.getOwner() != 0L) {
            User u = guild.getJDA().getUserById(rm.user.id);
            if(u == null) {
                currentEB.addField("Requested By", "<@" + rm.user.id + ">", false);
            } else {
                currentEB.addField("Requested By", u.getAsMention(), false);
            }
        }

        // Up Next
        if(!queue.isEmpty())
        {
            AudioTrack next = queue.get(0).getTrack();
            try {
                currentEB.addField("Next", "[" + curNext + "](" + next.getInfo().uri  + ")", false);
            } catch(Exception e) {
                currentEB.addField("Next", curNext, false);
            }
        } else {
            currentEB.addField("Next", "Nothing next in queue", false);
        }

        // Volume
        currentEB.addField("Volume", String.valueOf(curVolume) + "%", true);

        // Progress Bar
        double progress = (double)audioPlayer.getPlayingTrack().getPosition()/track.getDuration();
        currentEB.addField("", getStatusEmoji()
                + " "+FormatUtil.progressBar(progress)
                + " `[" + FormatUtil.formatTime(track.getPosition()) + "/" + FormatUtil.formatTime(track.getDuration()) + "]` "
                + FormatUtil.volumeIcon(audioPlayer.getVolume()), false);
        
        // Buttons
        Button pauseButton = Button.primary("pause", "Pause");
        Button playButton = Button.success("play", "Play");
        Button skipButton = Button.secondary("skip", "Skip");
        Button stopButton = Button.danger("stop", "Stop");
        mb.setActionRows(ActionRow.of(pauseButton, playButton, skipButton, stopButton));

        return mb.setEmbeds(currentEB.build()).build();
    }

    public Message getNoMusicPlaying(JDA jda)
    {
        Guild guild = guild(jda);
        MessageBuilder mb = new MessageBuilder();

        nowPlayingUpdated = false;

        // Check if track is different to avoid re-downloading the thumbnail
        if (!(new String("").equals(currentTitle))) {
            nowPlayingUpdated = true;

            // Cache EmbedBuilder
            currentEB = new EmbedBuilder();
            currentEB.setColor(guild.getSelfMember().getColor());

            // Cache title
            currentTitle = "";

            // Cache Thumbnail
            currentEB.setImage("https://img.youtube.com/vi/u8PGSCmXjNw/mqdefault.jpg");
        }

        // Check if volume has changed
        int curVolume = audioPlayer.getVolume();
        if (prevVolume != curVolume) {
            prevVolume = curVolume;
            nowPlayingUpdated = true;
        }

        if (nowPlayingUpdated) {
            currentEB.clearFields();

            // Currently Playing
            currentEB.addField("Currently Playing", "", false);
    
            // Artist
            currentEB.addField("By", "", false);
            
            // Requested By
            currentEB.addField("Requested By", "", false);
    
            // Up Next
            currentEB.addField("Next", "", false);
    
            // Volume
            currentEB.addField("Volume", String.valueOf(curVolume) + "%", true);
    
            // Progress Bar
            currentEB.addBlankField(false);

            Button pauseButton = Button.primary("pause", "Pause").asDisabled();
            Button playButton = Button.success("play", "Play").asDisabled();
            Button skipButton = Button.secondary("skip", "Skip").asDisabled();
            Button stopButton = Button.danger("stop", "Stop").asDisabled();
            mb.setActionRows(ActionRow.of(pauseButton, playButton, skipButton, stopButton));
        }

        mb.setEmbeds(currentEB.build());
        return mb.build();
    }
    
    public String getTopicFormat(JDA jda)
    {
        if(isMusicPlaying(jda))
        {
            long userid = getRequestMetadata().getOwner();
            AudioTrack track = audioPlayer.getPlayingTrack();
            String title = track.getInfo().title;
            if(title==null || title.equals("Unknown Title"))
                title = track.getInfo().uri;
            return "**"+title+"** ["+(userid==0 ? "autoplay" : "<@"+userid+">")+"]"
                    + "\n" + getStatusEmoji() + " "
                    + "[" + FormatUtil.formatTime(track.getDuration()) + "] "
                    + FormatUtil.volumeIcon(audioPlayer.getVolume());
        }
        else return "No music playing " + STOP_EMOJI + " " + FormatUtil.volumeIcon(audioPlayer.getVolume());
    }
    
    public String getStatusEmoji()
    {
        return audioPlayer.isPaused() ? PAUSE_EMOJI : PLAY_EMOJI;
    }
    
    // Audio Send Handler methods
    /*@Override
    public boolean canProvide() 
    {
        if (lastFrame == null)
            lastFrame = audioPlayer.provide();

        return lastFrame != null;
    }

    @Override
    public byte[] provide20MsAudio() 
    {
        if (lastFrame == null) 
            lastFrame = audioPlayer.provide();

        byte[] data = lastFrame != null ? lastFrame.getData() : null;
        lastFrame = null;

        return data;
    }*/
    
    @Override
    public boolean canProvide() 
    {
        lastFrame = audioPlayer.provide();
        return lastFrame != null;
    }

    @Override
    public ByteBuffer provide20MsAudio() 
    {
        return ByteBuffer.wrap(lastFrame.getData());
    }

    @Override
    public boolean isOpus() 
    {
        return true;
    }
    
    
    // Private methods
    private Guild guild(JDA jda)
    {
        return jda.getGuildById(guildId);
    }
}
