package com.seafucker.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(InGameHud.class)
public interface InGameHudAccessor {
    // "field_2018" è il codice segreto per il TITOLO
    @Accessor("field_2018")
    Text getTitle();

    // "field_2019" è il codice segreto per il SOTTOTITOLO
    @Accessor("field_2019")
    Text getSubtitle();

    // "field_2024" è il codice segreto per l'ACTION BAR (Overlay Message)
    @Accessor("field_2024")
    Text getOverlayMessage();
    
    @Accessor("field_2024")
    void setOverlayMessage(Text message);
}