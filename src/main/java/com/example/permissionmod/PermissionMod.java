//package com.example.permissionmod;
//
//import net.minecraft.command.CommandHandler;
//import net.minecraft.server.MinecraftServer;
//import net.minecraftforge.fml.common.Mod;
//import net.minecraftforge.fml.common.Mod.EventHandler;
//import net.minecraftforge.fml.common.event.FMLInitializationEvent;
//
//@Mod(modid = PermissionMod.MODID, version = PermissionMod.VERSION)
//public class PermissionMod
//{
//    public static final String MODID = "permissionmod";
//    public static final String VERSION = "1.0";
//
//    @EventHandler
//    public void init(FMLInitializationEvent event)
//    {
//        // Register command
//        CommandHandler commandHandler = new CommandHandler() {
//            @Override
//            protected MinecraftServer getServer() {
//                return null;
//            }
//        };
//        commandHandler.registerCommand(new PermissionCommand());
//    }
//}

package com.example.permissionmod;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Mod(modid = PermissionMod.MODID, version = PermissionMod.VERSION)
public class PermissionMod {
    public static final String MODID = "permissionmod";
    public static final String VERSION = "1.0";

    private File permissionsFile;
    private Map<UUID, PermissionsData> permissionsData = new HashMap<>();

    private Configuration config;

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);

        config = new Configuration(new File("config/permissionmod.cfg"));
        syncConfig();

        permissionsFile = new File("config/permission/permissions.json");
        loadPermissions();
    }

    private void syncConfig() {
        Property prop = config.get(Configuration.CATEGORY_GENERAL, "debug", false, "Enable debug mode");
        prop.setLanguageKey("permissionmod.config.debug");
        prop.setRequiresMcRestart(true);
        prop.setComment("Enable debug logging for the Permission Mod");

        if (config.hasChanged()) {
            config.save();
        }
    }

    private void loadPermissions() {
        if (!permissionsFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(permissionsFile)) {
            Gson gson = new Gson();
            permissionsData = gson.fromJson(reader, new TypeToken<Map<UUID, PermissionsData>>() {
            }.getType());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void savePermissions() {
        try (FileWriter writer = new FileWriter(permissionsFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(permissionsData, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new PermissionCommand());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerLoggedInEvent event) {


        UUID uuid = event.player.getUniqueID();

        if (!permissionsData.containsKey(uuid)) {
            permissionsData.put(uuid, new PermissionsData());
            savePermissions();
        }

        PermissionsData data = permissionsData.get(uuid);
        for (String node : data.getPermissions()) {
            event.player.getServer().getPlayerList().getOppedPlayers().getEntry(event.player.getGameProfile());
        }
    }

    private class PermissionCommand extends CommandBase {

        @Override
        public String getName() {
            return "permission";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/permission <add|remove> <player> <node>";
        }

        @Override
        public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
            return sender.canUseCommand(4, "permission");
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
            if (args.length < 3) {
                sender.sendMessage(new TextComponentString("Invalid arguments. Usage: /permission <add|remove> <player> <permission>"));
                return;
            }

            String mode = args[0];
            String playerName = args[1];
            String permission = args[2];

            UUID uuid = server.getPlayerProfileCache().getGameProfileForUsername(playerName).getId();
            if (uuid == null) {
                sender.sendMessage(new TextComponentString("Player not found."));
                return;
            }

            PermissionsData data = permissionsData.get(uuid);
            if (data == null) {
                data = new PermissionsData();
                permissionsData.put(uuid, data);
            }

            if (mode.equals("add")) {
                data.addPermission(permission);
                sender.sendMessage(new TextComponentString("Added permission " + permission + " to " + playerName));
            } else if (mode.equals("remove")) {
                data.removePermission(permission);
                sender.sendMessage(new TextComponentString("Removed permission " + permission + " from " + playerName));
            } else {
                sender.sendMessage(new TextComponentString("Invalid mode. Usage: /permission <add|remove> <player> <permission>"));
            }

            savePermissions();
        }
    }

    private class PermissionsData {
        private final Map<String, Boolean> permissions = new HashMap<>();

        public PermissionsData() {
        }

        public void addPermission(String permission) {
            permissions.put(permission, true);
        }

        public void removePermission(String permission) {
            permissions.remove(permission);
        }

        public boolean hasPermission(String permission) {
            Boolean value = permissions.get(permission);
            return value != null && value;
        }

        public Iterable<String> getPermissions() {
            return permissions.keySet();
        }
    }

    private class PermissionsFileParser {
        private Gson gson = new Gson();

        public Map<UUID, PermissionsData> parse(File file) throws IOException {
            Map<UUID, PermissionsData> data = new HashMap<>();

            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> map = gson.fromJson(reader, new TypeToken<Map<String, Object>>() {
                }.getType());
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    UUID uuid = UUID.fromString(entry.getKey());
                    PermissionsData permissionsData = gson.fromJson(gson.toJson(entry.getValue()), PermissionsData.class);
                    data.put(uuid, permissionsData);
                }
            }

            return data;
        }

        public void save(File file, Map<UUID, PermissionsData> data) throws IOException {
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<UUID, PermissionsData> entry : data.entrySet()) {
                String uuid = entry.getKey().toString();
                Object permissionsData = gson.fromJson(gson.toJson(entry.getValue()), Object.class);
                map.put(uuid, permissionsData);
            }

            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(map, writer);
            }
        }
    }
}


