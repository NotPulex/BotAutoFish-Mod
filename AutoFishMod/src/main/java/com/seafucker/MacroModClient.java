package com.seafucker;

import com.seafucker.mixin.InGameHudAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public class MacroModClient implements ClientModInitializer {
    private Text lastTitle = null;
    private long lastRightClickTime = 0L;
    private long lastActionTime = 0L; 
    private boolean fishingEnabled = false;
    private boolean hasClickedSinceNoTitle = false;
    private KeyBinding toggleKey;

    // Colori target aggiornati
    private static final int[] TARGET_COLORS = {
        5540054, // NUOVO COLORE CORRETTO (Richiesto dall'utente)
        5635925, // Verde Lime
        43520,   // Verde Scuro
        16755200,// Oro
        5636095  // Ciano
    };

    @Override
    public void onInitializeClient() {
        this.toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.fishing.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "category.kedafucker"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            
            while (this.toggleKey.wasPressed()) { 
                if (!this.fishingEnabled) {
                    if (this.isHoldingFishingRod(client)) {
                        this.fishingEnabled = true;
                        this.hasClickedSinceNoTitle = false;
                        this.lastTitle = null;
                        this.lastActionTime = 0;
                        this.sendMessage(client, "\u00A7a[Macro ON] \u00A7fAnti-Spam attivo. Colore target: 5540054");
                    } else {
                        this.sendMessage(client, "\u00A7c[Errore] \u00A7fDevi tenere una canna da pesca in mano!");
                    }
                } else {
                    this.fishingEnabled = false;
                    this.sendMessage(client, "\u00A7c[Macro OFF] \u00A7fDisattivata.");
                }
            }

            if (client == null || client.player == null || client.inGameHud == null) return;
            if (!this.fishingEnabled) return;

            if (!this.isHoldingFishingRod(client)) {
                this.fishingEnabled = false;
                this.sendMessage(client, "\u00A7c[Stop] \u00A7fCanna rimossa.");
                return;
            }

            if (this.isInventoryFull(client)) {
                this.fishingEnabled = false;
                this.sendMessage(client, "\u00A7c[Stop] \u00A7fInventario pieno!");
                return;
            }

            InGameHudAccessor hud = (InGameHudAccessor) client.inGameHud;
            Text title = hud.getTitle();

            long now = System.currentTimeMillis();
            if (title == null && !this.hasClickedSinceNoTitle && (now - this.lastActionTime > 1500)) {
                this.performRightClick(client);
                this.hasClickedSinceNoTitle = true;
                this.lastActionTime = now;
            }
            if (title != null) this.hasClickedSinceNoTitle = false;

            if (title != null && !title.equals(this.lastTitle)) {
                this.lastTitle = title;
                
                Map<Integer, Integer> colorMap = this.countCharactersByColor(title);
                if (!colorMap.isEmpty()) {
                    colorMap.forEach((k,v) -> {
                        // Filtra i colori comuni per pulire la chat
                        if (k != 11184810 && k != 43690) { 
                            this.sendMessage(client, "\u00A7b[DEBUG] \u00A7fVedo Colore ID: \u00A7e" + k);
                        }
                    });
                }

                if (now - this.lastActionTime > 1000) {
                    for (int target : TARGET_COLORS) {
                        if (colorMap.getOrDefault(target, 0) > 3) { 
                            this.sendMessage(client, "\u00A7a[PESCA] \u00A7fColore GIUSTO (" + target + ")! Tiro su.");
                            this.performRightClick(client);
                            this.lastActionTime = now;
                            break;
                        }
                    }
                }
            }
            
            if (title == null && this.lastTitle != null) {
                this.lastTitle = null;
            }
        });
    }

    private boolean isHoldingFishingRod(MinecraftClient client) {
        if (client.player == null) return false;
        String itemKey = client.player.getMainHandStack().getItem().getTranslationKey();
        return itemKey != null && itemKey.contains("fishing_rod");
    }

    private void sendMessage(MinecraftClient client, String text) {
        if (client.player != null) {
            client.player.sendMessage(Text.of(text), false);
        }
    }

    private Map<Integer, Integer> countCharactersByColor(Text text) {
        final HashMap<Integer, Integer> map = new HashMap<>();
        
        text.visit((style, segment) -> {
            if (segment == null || segment.isEmpty()) return Optional.empty();
            if (style.getColor() == null) return Optional.empty();
            
            int rgb = style.getColor().getRgb();
            int length = (int)segment.chars().filter(c -> c != 32).count();
            
            if (length > 0) map.merge(rgb, length, Integer::sum);
            return Optional.empty();
        }, Style.EMPTY);
        
        return map;
    }

    private void performRightClick(MinecraftClient client) {
        client.execute(() -> {
            if (client.player == null || client.interactionManager == null) return;
            client.player.swingHand(Hand.MAIN_HAND);
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        });
    }

    private boolean isInventoryFull(MinecraftClient client) {
        if (client.player == null) return false;
        for (int i = 9; i < 36; i++) {
            if (client.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }
}