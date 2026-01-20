package me.drex.betteritemviewer.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class BetterItemViewerConfig {
    public static final BuilderCodec<BetterItemViewerConfig> CODEC = BuilderCodec.builder(BetterItemViewerConfig.class, BetterItemViewerConfig::new)
        .append(new KeyedCodec<>("DefaultAltKeyBind", Codec.BOOLEAN), (o, i) -> o.defaultAltKeybind = i, o -> o.defaultAltKeybind)
        .add()
        .build();

    public boolean defaultAltKeybind = true;
}
