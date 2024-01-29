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
package com.jagrosh.jmusicbot.commands.dj;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.DJCommand;
import com.jagrosh.jmusicbot.utils.FormatUtil;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class EqualizerCmd extends DJCommand
{
    public EqualizerCmd(Bot bot)
    {
        super(bot);
        this.name = "equalizer";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.help = "sets or shows volume";
        this.arguments = "<bass|outside|normal>";
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
        int volume = handler.getPlayer().getVolume();
        if(event.getArgs().isEmpty())
        {
            event.reply(FormatUtil.volumeIcon(volume)+" Current volume is `"+volume+"`");
        }
        else
        {
            final float[] BASS_BOOST = {
                -0.25f,
                -0.25f,
                -0.25f,
                -0.25f,
                -0.25f,
                -0.25f,
                -0.25f,
                -0.25f,
                -0.25f,
                -0.25f,
                -0.25f,
                -0.25f,
                -0.25f,
                -0.25f,
                -0.25f,
            };

            String mode = event.getArgs();
            event.reply(mode);
            if (mode.equals("reset"))
            {
                //handler.resetEQ();
                for (int i = 0; i < BASS_BOOST.length; i++)
                {
                    handler.getEqualizer().setGain(i, 0f);
                }
            }
            else if (mode.equals("outside"))
            {
                //handler.resetEQ();
                for (int i = 0; i < BASS_BOOST.length; i++)
                {
                    handler.getEqualizer().setGain(i, BASS_BOOST[i] * 2.5f);
                }
            }
            else
            {
                event.reply(event.getClient().getError()+" EQ must be outside or reset.");
            }
        }
    }
    
}
