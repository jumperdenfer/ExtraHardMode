/*
 * This file is part of
 * ExtraHardMode Server Plugin for Minecraft
 *
 * Copyright (C) 2012 Ryan Hamshire
 * Copyright (C) 2013 Diemex
 *
 * ExtraHardMode is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ExtraHardMode is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License
 * along with ExtraHardMode.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.extrahardmode.module;


import com.extrahardmode.ExtraHardMode;
import com.extrahardmode.config.messages.MessageConfig;
import com.extrahardmode.config.messages.MessageNode;
import com.extrahardmode.service.EHMModule;
import com.extrahardmode.service.FindAndReplace;
import com.extrahardmode.service.PermissionNode;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import me.diemex.sbpopupapi.MessageType;
import me.diemex.sbpopupapi.SBPopupAPI;
import me.diemex.sbpopupapi.SBPopupManager;
import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Calendar;

/**
 * @author Max
 */
public class MessagingModule extends EHMModule
{
    private final MessageConfig messages;

    private final MsgPersistModule persistModule;

    private SBPopupManager manager;

    private final Table<String, MessageNode, Long> timeouts = HashBasedTable.create();


    /**
     * Constructor
     */
    public MessagingModule(ExtraHardMode plugin)
    {
        super(plugin);
        messages = plugin.getModuleForClass(MessageConfig.class);
        persistModule = plugin.getModuleForClass(MsgPersistModule.class);
        try
        {
            if (null != Class.forName("me.diemex.sbpopupapi.SBPopupAPI"))
                manager = SBPopupAPI.getSBManager();
        } catch (ClassNotFoundException e)
        {
            e.printStackTrace();
        }
    }


    private void send(Player player, MessageNode node, String message, Type type)
    {
        switch (type)
        {
            case NOTIFICATION:
                if (player == null)
                {
                    plugin.getLogger().warning("Could not send the following message: " + message);
                } else
                {
                    // FEATURE: don't spam messages
                    DataStoreModule.PlayerData playerData = plugin.getModuleForClass(DataStoreModule.class).getPlayerData(player.getName());
                    long now = Calendar.getInstance().getTimeInMillis();

                    if (!node.equals(playerData.lastMessageSent) || now - playerData.lastMessageTimestamp > 30000)
                    {
                        if (manager != null)
                            manager.showPopup(player.getName(), MessageType.NOTFICATION, "ExtraHardMode", message);
                        else
                            player.sendMessage(message);
                        playerData.lastMessageSent = node;
                        playerData.lastMessageTimestamp = now;
                    }

                }
                break;
            case TUTORIAL:
                Validate.notNull(player);
                if (persistModule.getCountFor(node, player.getName()) < node.getMsgCount())
                {
                    long now = Calendar.getInstance().getTimeInMillis();

                    if (!timeouts.contains(player.getName(), message) || now - timeouts.get(player.getName(), message) > 120000)
                    {
                        timeouts.put(player.getName(), node, now);
                        String msgText = messages.getString(node);
                        if (manager != null)
                            manager.showPopup(player.getName(), MessageType.WARNING, "ExtraHardMode", msgText);
                        else
                            player.sendMessage(ChatColor.DARK_RED + plugin.getTag() + ChatColor.WHITE + " " + msgText);
                        persistModule.increment(node, player.getName());
                    }
                } else
                    timeouts.remove(player, message);
                break;
            case BROADCAST:
                plugin.getServer().broadcastMessage(message);
                break;
        }
    }


    /**
     * Broacast a message to the whole server
     *
     * @param node
     *         message to broadcast
     * @param replace
     *         replace these placeholders
     */
    public void broadcast(MessageNode node, FindAndReplace... replace)
    {
        send(null, node, Type.BROADCAST, replace);
    }


    /**
     * Send a message to a Player.
     *
     * @param player
     *         to send the message to
     * @param node
     *         message, gets loaded from the config
     * @param type
     *         type determnines the display lenght and color
     */
    public void send(Player player, MessageNode node, Type type)
    {
        send(player, node, messages.getString(node), type);
    }


    /**
     * Send a message to a Player. Default type is NOTIFICATION.
     *
     * @param player
     *         to send the message to
     * @param node
     *         message, gets loaded from the config
     */
    public void send(Player player, MessageNode node)
    {
        send(player, node, messages.getString(node), Type.NOTIFICATION);
    }


    /**
     * Sends a message with variables which will be inserted in the specified areas
     *
     * @param player
     *         Player to send the message to
     * @param message
     *         to send
     */
    public void send(Player player, MessageNode message, Type type, FindAndReplace... fars)
    {
        String msgText = messages.getString(message);
        for (FindAndReplace far : fars)
        {   /* Replace the placeholder with the actual value */
            msgText = msgText.replace(far.getSearchWord(), far.getReplaceWith());
        }
        send(player, message, msgText, type);
    }


    /**
     * Send the player an informative message to explain what he's doing wrong. Play an optional sound aswell
     * <p/>
     *
     * @param player
     *         to send msg to
     * @param perm
     *         permission to silence the message
     * @param sound
     *         errorsound to play after the event got cancelled
     * @param soundPitch
     *         20-35 is good
     */
    public void send(Player player, MessageNode node, PermissionNode perm, Sound sound, float soundPitch)
    {
        if (!player.hasPermission(perm.getNode()))
        {
            send(player, node, messages.getString(node), Type.NOTIFICATION);
            if (sound != null)
                player.playSound(player.getLocation(), sound, 1, soundPitch);
        }
    }


    /**
     * Send the player an informative message to explain what he's doing wrong.
     * <p/>
     *
     * @param player
     *         to send msg to
     * @param node
     *         the message
     * @param perm
     *         permission to silence the message
     */
    public void send(Player player, MessageNode node, PermissionNode perm)
    {
        send(player, node, perm, null, 0);
    }


    public enum Type
    {
        NOTIFICATION,
        TUTORIAL,
        BROADCAST
    }


    @Override
    public void starting()
    {
    }


    @Override
    public void closing()
    {
    }
}
