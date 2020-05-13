package ro.nicuch.tag.register;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import ro.nicuch.tag.TagRegister;
import ro.nicuch.tag.events.RegionTagLoadEvent;
import ro.nicuch.tag.fallback.CoruptedDataFallback;
import ro.nicuch.tag.fallback.CoruptedDataManager;
import ro.nicuch.tag.nbt.CompoundTag;
import ro.nicuch.tag.nbt.Tag;
import ro.nicuch.tag.nbt.TagIO;
import ro.nicuch.tag.nbt.TagType;
import ro.nicuch.tag.nbt.reg.RegionCompoundTag;
import ro.nicuch.tag.wrapper.ChunkUUID;
import ro.nicuch.tag.wrapper.RegionUUID;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RegionRegister implements CoruptedDataFallback {
    private final WorldRegister register;
    private final ConcurrentMap<ChunkUUID, ChunkRegister> chunks = new ConcurrentHashMap<>();
    private final int x;
    private final int z;
    private final File regionFile;
    private RegionCompoundTag regionTag;

    private final RegionUUID uuid;

    public final RegionUUID getRegionUUID() {
        return this.uuid;
    }

    public RegionRegister(WorldRegister register, Chunk chunk) {
        this(register, Math.floorDiv(chunk.getX(), 32), Math.floorDiv(chunk.getZ(), 32));
    }

    public RegionRegister(WorldRegister register, int x, int z) {
        this.uuid = new RegionUUID(x, z);
        this.register = register;
        this.x = x;
        this.z = z;
        this.regionFile = new File(register.getDirectory().getPath() + File.separator + "r." + x + "." + z + ".dat");
        if (!this.regionFile.exists()) {
            this.regionTag = new RegionCompoundTag();
        } else {
            try {
                this.regionTag = (RegionCompoundTag) TagIO.readFile(this.regionFile, TagType.REGION_COMPOUND);
            } catch (IOException | NullPointerException ioe) {
                ioe.printStackTrace();
                System.out.println("(Reading) This region is corupted. -> r." + x + "." + z + ".dat!!");
            }
        }
        Bukkit.getScheduler().runTask(TagRegister.getPlugin(), () ->
                Bukkit.getPluginManager().callEvent(new RegionTagLoadEvent(this, this.regionTag)));
    }

    public WorldRegister getWorldRegister() {
        return this.register;
    }

    public RegionCompoundTag getRegionTag() {
        return this.regionTag;
    }

    public int getX() {
        return this.x;
    }

    public int getZ() {
        return this.z;
    }

    protected void writeRegionFile() {
        if (this.regionTag.isEmpty(true)) {
            if (this.regionFile.exists())
                this.regionFile.delete();
            return;
        }
        if (!this.regionFile.exists()) {
            try {
                this.regionFile.createNewFile();
                this.regionFile.setReadable(true, false);
                this.regionFile.setWritable(true, false);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            TagIO.writeFile(this.regionTag, this.regionFile);
        } catch (IOException | NullPointerException ioe) {
            ioe.printStackTrace();
            System.out.println("(Writing) This region file is corupted. -> r." + x + "." + z + ".dat");
            CoruptedDataManager.fallbackOperation(this);
        }
    }

    public boolean isChunkNotLoaded(Chunk chunk) {
        ChunkUUID chunkUUID = new ChunkUUID(chunk.getX(), chunk.getZ());
        return !this.chunks.containsKey(chunkUUID);
    }

    public boolean isChunkNotLoaded(ChunkUUID chunkUUID) {
        return !this.chunks.containsKey(chunkUUID);
    }

    public ChunkRegister loadChunk(Chunk chunk) {
        ChunkRegister chunkRegister = new ChunkRegister(this, chunk);
        this.chunks.put(chunkRegister.getChunkUUID(), chunkRegister);
        return chunkRegister;
    }

    public Optional<ChunkRegister> getChunk(Chunk chunk) {
        ChunkUUID chunkUUID = new ChunkUUID(chunk.getX(), chunk.getZ());
        return Optional.ofNullable(this.chunks.get(chunkUUID));
    }

    public Optional<ChunkRegister> getChunk(ChunkUUID chunkUUID) {
        return Optional.ofNullable(this.chunks.get(chunkUUID));
    }

    public ChunkRegister removeChunk(Chunk chunk) {
        ChunkUUID chunkUUID = new ChunkUUID(chunk.getX(), chunk.getZ());
        return this.chunks.remove(chunkUUID);
    }

    public ChunkRegister unloadChunk(Chunk chunk, Set<UUID> entitiesArray) {
        Optional<ChunkRegister> chunkRegisterOptional = this.getChunk(chunk);
        if (chunkRegisterOptional.isPresent()) {
            ChunkRegister chunkRegister = chunkRegisterOptional.get();
            chunkRegister.unload(true, entitiesArray);
            return this.chunks.remove(chunkRegister.getChunkUUID());
        }
        return null;
    }

    public boolean canBeUnloaded() {
        Iterator<ChunkRegister> chunkIterator = this.chunks.values().iterator();
        while (chunkIterator.hasNext()) {
            ChunkRegister chunkRegister = chunkIterator.next();
            if (!chunkRegister.getChunk().isLoaded()) {
                chunkRegister.unload(false, null);
                chunkIterator.remove();
            }
        }
        return this.chunks.isEmpty();
    }

    public void saveChunks() {
        for (ChunkRegister chunk : this.chunks.values())
            if (chunk.getChunk().isLoaded()) {
                Set<UUID> entitiesUUID = new HashSet<>();
                for (Entity entity : chunk.getChunk().getEntities())
                    entitiesUUID.add(entity.getUniqueId());
                chunk.savePopulation(true, entitiesUUID);
            } else
                chunk.savePopulation(false, null);
    }

    public boolean isBlockStored(Block block) {
        return this.getChunk(block.getChunk()).orElseGet(() -> this.loadChunk(block.getChunk())).isBlockStored(block);
    }

    public Optional<CompoundTag> getStoredBlock(Block block) {
        return this.getChunk(block.getChunk()).orElseGet(() -> this.loadChunk(block.getChunk())).getStoredBlock(block);
    }

    public CompoundTag createStoredBlock(Block block) {
        return this.getChunk(block.getChunk()).orElseGet(() -> this.loadChunk(block.getChunk())).createStoredBlock(block);
    }

    public boolean isEntityStored(Entity entity) {
        return this.isEntityStored(entity.getUniqueId());
    }

    public boolean isEntityStored(UUID uuid) {
        return this.register.isEntityStoredInternal(uuid);
    }

    public Optional<CompoundTag> getStoredEntity(Entity entity) {
        return this.getStoredEntity(entity.getUniqueId());
    }

    public CompoundTag createStoredEntity(Entity entity) {
        return this.createStoredEntity(entity.getUniqueId());
    }

    public Optional<CompoundTag> getStoredEntity(UUID uuid) {
        return this.register.getStoredEntityInternal(uuid);
    }

    public CompoundTag createStoredEntity(UUID uuid) {
        return this.register.createStoredEntityInternal(uuid);
    }

    public RegionRegister setRegionTag(RegionCompoundTag tag) {
        this.regionTag = tag;
        return this;
    }

    @Override
    public String getCoruptedDataId() {
        return "r." + x + "." + z + "_region";
    }

    @Override
    public File getCoruptedDataFile() {
        return this.regionFile;
    }

    @Override
    public Tag getCoruptedDataCompoundTag() {
        return this.regionTag;
    }

    @Override
    public String getWorldName() {
        return this.register.getWorldInstance().getName();
    }

    @Override
    public String toString() {
        return "RegionRegister{" +
                "x: " + this.uuid.getX() +
                ", y: " + this.uuid.getZ() +
                ", w: " + this.register.getWorldInstance().getName() +
                "}";
    }
}
