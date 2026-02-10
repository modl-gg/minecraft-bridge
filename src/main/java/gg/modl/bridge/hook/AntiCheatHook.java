package gg.modl.bridge.hook;

public interface AntiCheatHook {

    String getName();

    boolean isAvailable();

    void register();

    void unregister();
}
