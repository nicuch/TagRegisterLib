package ro.nicuch.tag.events;

import ro.nicuch.lwjnbtl.CompoundTag;
import ro.nicuch.tag.register.WorldRegister;

public class WorldTagLoadEvent extends TagLoadEvent {
    private final WorldRegister worldRegister;

    public WorldTagLoadEvent(WorldRegister worldRegister, CompoundTag tag) {
        super(tag);
        this.worldRegister = worldRegister;
    }

    public WorldRegister getWorldRegister() {
        return this.worldRegister;
    }
}
