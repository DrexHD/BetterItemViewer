package me.drex.betteritemviewer.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;

import java.util.HashSet;
import java.util.Set;

public class BetterItemViewerConfig {
    public static final BuilderCodec<BetterItemViewerConfig> CODEC = BuilderCodec.builder(BetterItemViewerConfig.class, BetterItemViewerConfig::new)
        .append(new KeyedCodec<>("DefaultAltKeyBind", Codec.BOOLEAN), (o, i) -> o.defaultAltKeybind = i, o -> o.defaultAltKeybind)
        .add()
        .append(new KeyedCodec<>("DisableCommand", Codec.BOOLEAN), (o, i) -> o.disableCommand = i, o -> o.disableCommand)
        .add()
        .append(new KeyedCodec<>("DisableKeybind", Codec.BOOLEAN), (o, i) -> o.disableKeybind = i, o -> o.disableKeybind)
        .add()
        .append(new KeyedCodec<>("DisableModFilter", Codec.BOOLEAN), (o, i) -> o.disableModFilter = i, o -> o.disableModFilter)
        .add()
        .append(new KeyedCodec<>("DisableCreatorInfoSetting", Codec.BOOLEAN), (o, i) -> o.disableCreatorInfoSetting = i, o -> o.disableCreatorInfoSetting)
        .add()
        .append(new KeyedCodec<>("DisableHiddenItemsSetting", Codec.BOOLEAN), (o, i) -> o.disableHiddenItemsSetting = i, o -> o.disableHiddenItemsSetting)
        .add()
        .append(new KeyedCodec<>("HiddenQualities", new SetCodec<>(Codec.STRING, HashSet::new, true)), (o, i) -> o.hiddenQualities = i, o -> o.hiddenQualities)
        .add()
        .build();

    public boolean defaultAltKeybind = true;
    public boolean disableCommand = false;
    public boolean disableKeybind = false;
    public boolean disableModFilter = false;
    public boolean disableCreatorInfoSetting = false;
    public boolean disableHiddenItemsSetting = false;
    public Set<String> hiddenQualities = Set.of("Developer", "Tool", "Technical");
}
