package com.seafucker;

import com.seafucker.mixin.InGameHudAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MacroModClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("kedagay-bot");
    private final Random random = new Random();

    // TASTI
    private KeyBinding toggleKey; 
    private KeyBinding debugKey;  
    private KeyBinding configKey; 

    // STATI
    private boolean botEnabled = false;
    private boolean debugEnabled = false;
    
    // IMPOSTAZIONI CONFIGURABILI
    public static int maxRecastDelay = 20; 
    public static boolean antiAfkEnabled = true;

    // VARIABILI INTERNE
    private int clickCooldown = 0;
    private int castRodDelay = 0;
    private int warningDelay = 0; 
    private String lastDebugText = "";

    // TIMER ANTI-AFK
    private int afkLookTimer = 0; 
    private int timerA = 6000;    
    private int timerD = 6100;    
    private boolean releaseA = false;
    private boolean releaseD = false;

    // COLORI
    private static final String COLOR_CURSOR = "dfedee"; 
    private static final String COLOR_TARGET = "5488d6"; 
    private static final String BAR_CHAR = "▬";

    @Override
    public void onInitializeClient() {
        // REGISTRAZIONE TASTI - Categoria "AutoFishMod"
        this.toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Attiva/Disattiva Fishing", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "AutoFishMod"));

        this.debugKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Debug Colori", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "AutoFishMod"));
        
        this.configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Config Bot", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "AutoFishMod"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Timer
            if (clickCooldown > 0) clickCooldown--;
            if (castRodDelay > 0) castRodDelay--;
            if (warningDelay > 0) warningDelay--;

            // --- GESTIONE INPUT ---
            while (this.toggleKey.wasPressed()) {
                this.botEnabled = !this.botEnabled;
                sendPrivateMessage(client, "§b[KedaBot] §fFishing: " + (this.botEnabled ? "§aON" : "§cOFF"));
            }
            while (this.debugKey.wasPressed()) {
                this.debugEnabled = !this.debugEnabled;
                sendPrivateMessage(client, "§6[KedaDebug] §fAnalisi: " + (this.debugEnabled ? "§aON" : "§cOFF"));
                lastDebugText = ""; 
            }
            while (this.configKey.wasPressed()) {
                client.setScreen(new FishingConfigScreen());
            }

            if (!botEnabled && !debugEnabled) return;

            // --- 2. RADAR PLAYER (Con Cambio Slot Reflection) ---
            if (botEnabled && isPlayerNearby(client, 8.0)) {
                this.botEnabled = false; 
                
                if (client.player.fishHook != null) {
                    forceChangeSlot(client);
                }
                
                sendPrivateMessage(client, "§c[RADAR] §4GIOCATORE RILEVATO! §cBot Spento.");
                return; 
            }

            // --- 3. ANTI-AFK ---
            if (botEnabled && antiAfkEnabled) {
                handleAntiAfk(client);
            }

            // --- 4. CONTROLLI BASE ---
            if (!(client.player.getMainHandStack().getItem() instanceof FishingRodItem)) {
                if (warningDelay == 0 && botEnabled) {
                    sendPrivateMessage(client, "§e[KedaBot] §cPausa: Prendi in mano una canna da pesca!");
                    warningDelay = 100; 
                }
                return;
            }

            // Controllo Inventario Pieno (Con Cambio Slot Reflection)
            if (botEnabled && isInventoryFull(client)) {
                this.botEnabled = false;
                
                if (client.player.fishHook != null) {
                    forceChangeSlot(client);
                }
                
                sendPrivateMessage(client, "§c[KedaBot] Inventario pieno! Pesca interrotta.");
                return;
            }

            // --- 5. LOGICA DI PESCA ---
            InGameHudAccessor hud = (InGameHudAccessor) client.inGameHud;
            Text title = hud.getTitle();

            if (title == null || title.getString().trim().isEmpty()) {
                if (botEnabled && client.player.fishHook == null && castRodDelay == 0) {
                    rightClick(client);
                    castRodDelay = 40; 
                }
                return;
            }

            if (debugEnabled) analyzeColorsForDebug(client, title);
            if (botEnabled) analyzeAndFish(client, title);
        });
    }

    // --- FUNZIONE FORCE CHANGE SLOT (REFLECTION) ---
    // Questa funzione bypassa i controlli di sicurezza usando la "forza bruta"
    private void forceChangeSlot(MinecraftClient client) {
        if (client.player == null) return;
        try {
            // Proviamo a cercare il campo con il nome in codice "field_7545" (Produzione/Server)
            Field f = net.minecraft.entity.player.PlayerInventory.class.getDeclaredField("field_7545");
            f.setAccessible(true); // Scassiniamo la serratura (Rendiamo pubblico il campo privato)
            int current = f.getInt(client.player.getInventory());
            f.setInt(client.player.getInventory(), (current + 1) % 9);
        } catch (Exception e) {
            try {
                // Se fallisce (magari siamo in Dev), proviamo il nome "selectedSlot"
                Field f2 = net.minecraft.entity.player.PlayerInventory.class.getDeclaredField("selectedSlot");
                f2.setAccessible(true);
                int current = f2.getInt(client.player.getInventory());
                f2.setInt(client.player.getInventory(), (current + 1) % 9);
            } catch (Exception ex) {
                // Se fallisce tutto, pazienza, non crashiamo il gioco
                LOGGER.error("Impossibile cambiare slot: " + ex.getMessage());
            }
        }
    }

    // --- FUNZIONE RADAR ---
    private boolean isPlayerNearby(MinecraftClient client, double radius) {
        if (client.world == null || client.player == null) return false;
        
        for (AbstractClientPlayerEntity otherPlayer : client.world.getPlayers()) {
            if (otherPlayer.getUuid().equals(client.player.getUuid())) continue;
            
            if (client.player.distanceTo(otherPlayer) <= radius) {
                return true;
            }
        }
        return false;
    }

    // --- LOGICA ANTI-AFK ---
    private void handleAntiAfk(MinecraftClient client) {
        if (releaseA) {
            client.options.leftKey.setPressed(false);
            releaseA = false;
        }
        if (releaseD) {
            client.options.rightKey.setPressed(false);
            releaseD = false;
        }

        if (timerA > 0) timerA--;
        else {
            client.options.leftKey.setPressed(true); 
            releaseA = true; 
            timerA = 6000;   
        }

        if (timerD > 0) timerD--;
        else {
            client.options.rightKey.setPressed(true); 
            releaseD = true; 
            timerD = 6100;   
        }

        if (afkLookTimer > 0) afkLookTimer--;
        else {
            float pitchChange = (random.nextFloat() - 0.5f) * 0.5f; 
            float yawChange = (random.nextFloat() - 0.5f) * 0.5f;
            client.player.setYaw(client.player.getYaw() + yawChange);
            client.player.setPitch(client.player.getPitch() + pitchChange);
            afkLookTimer = 100 + random.nextInt(300);
        }
    }

    // --- ALTRE FUNZIONI ---
    private void analyzeColorsForDebug(MinecraftClient client, Text title) {
        String currentTitleString = title.getString();
        if (currentTitleString.equals(lastDebugText)) return;
        lastDebugText = currentTitleString;

        List<Text> siblings = title.getSiblings();
        Set<String> foundColors = new HashSet<>();

        for (Text part : siblings) {
            if (!part.getString().contains(BAR_CHAR)) continue;
            TextColor color = part.getStyle().getColor();
            if (color != null) {
                String hex = String.format("%06x", color.getRgb());
                foundColors.add(hex);
            }
        }

        if (!foundColors.isEmpty()) {
            String colorList = String.join(", ", foundColors);
            sendPrivateMessage(client, "§6[DEBUG] §fColori: §e" + colorList);
            if (client.keyboard != null) {
                client.keyboard.setClipboard(colorList);
                sendPrivateMessage(client, "§a[✔] Copiati negli appunti.");
            }
        }
    }

    private void analyzeAndFish(MinecraftClient client, Text title) {
        if (clickCooldown > 0) return;

        int cursorIndex = -1;
        int targetIndex = -1;
        int barCounter = 0;

        List<Text> siblings = title.getSiblings();
        
        for (Text part : siblings) {
            String text = part.getString();
            if (!text.contains(BAR_CHAR)) continue;
            TextColor color = part.getStyle().getColor();
            if (color == null) continue;
            String hex = String.format("%06x", color.getRgb());

            if (hex.equalsIgnoreCase(COLOR_CURSOR)) {
                cursorIndex = barCounter;
            } else if (hex.equalsIgnoreCase(COLOR_TARGET)) {
                targetIndex = barCounter;
            }
            barCounter++;
        }

        boolean shouldClick = false;
        if (cursorIndex != -1) {
            if ((targetIndex != -1 && cursorIndex == targetIndex) || targetIndex == -1) {
                shouldClick = true; 
            }
        }

        if (shouldClick) {
            rightClick(client);
            client.inGameHud.setOverlayMessage(Text.of("§a⚡ CLICK!"), false);
            clickCooldown = 5; 
            int randomDelay = maxRecastDelay > 0 ? random.nextInt(maxRecastDelay + 1) : 0;
            castRodDelay = 5 + randomDelay; 
        }
    }

    private void sendPrivateMessage(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.of(message), false);
        }
    }

    private void rightClick(MinecraftClient client) {
        if (client.interactionManager != null && client.player != null) {
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            client.player.swingHand(Hand.MAIN_HAND);
        }
    }

    // Utilizziamo il metodo getStack(i) che è sicuro e pubblico
    private boolean isInventoryFull(MinecraftClient client) {
        if (client.player == null) return false;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.isEmpty()) return false; 
        }
        return true; 
    }

    // --- GUI CONFIGURAZIONE ---
    public static class FishingConfigScreen extends Screen {

        public FishingConfigScreen() {
            super(Text.of("KedaBot Config"));
        }

        @Override
        protected void init() {
            this.addDrawableChild(ButtonWidget.builder(Text.of("-"), button -> {
                if (MacroModClient.maxRecastDelay > 0) MacroModClient.maxRecastDelay--;
            }).dimensions(this.width / 2 - 55, this.height / 2 - 10, 20, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.of("+"), button -> {
                if (MacroModClient.maxRecastDelay < 100) MacroModClient.maxRecastDelay++;
            }).dimensions(this.width / 2 + 35, this.height / 2 - 10, 20, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                Text.of("Anti-AFK: " + (MacroModClient.antiAfkEnabled ? "§aON" : "§cOFF")),
                button -> {
                    MacroModClient.antiAfkEnabled = !MacroModClient.antiAfkEnabled;
                    button.setMessage(Text.of("Anti-AFK: " + (MacroModClient.antiAfkEnabled ? "§aON" : "§cOFF")));
                }
            ).dimensions(this.width / 2 - 50, this.height / 2 + 20, 100, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.of("Salva e Chiudi"), button -> {
                this.close();
            }).dimensions(this.width / 2 - 50, this.height / 2 + 50, 100, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0xA0000000); 
            super.render(context, mouseX, mouseY, delta);

            context.drawCenteredTextWithShadow(this.textRenderer, Text.of("§bConfigurazione KedaBot"), this.width / 2, this.height / 2 - 60, 0xFFFFFFFF);
            
            float seconds = MacroModClient.maxRecastDelay / 20.0f;
            String secondsText = String.format("Ritardo Max: §a%.2f secondi", seconds);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.of(secondsText), this.width / 2, this.height / 2 - 25, 0xFFFFFFFF);
            
            String ticksText = "§8(" + MacroModClient.maxRecastDelay + " ticks)";
            context.drawCenteredTextWithShadow(this.textRenderer, Text.of(ticksText), this.width / 2, this.height / 2 - 5, 0xAAAAAAFF);
        }
        
        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}