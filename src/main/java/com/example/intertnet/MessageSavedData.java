package com.example.intertnet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

final class MessageSavedData extends SavedData {
    private final List<DeliveryManager.ChatMessage> messages = new ArrayList<>();

    static MessageSavedData get(ServerLevel level) {
        // Keep the original save file so existing worlds retain their message history.
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MessageSavedData::new, MessageSavedData::load),
                "intertnet_village_towers"
        );
    }

    private static MessageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MessageSavedData data = new MessageSavedData();
        for (Tag entry : tag.getList("Messages", Tag.TAG_COMPOUND)) {
            CompoundTag message = (CompoundTag) entry;
            data.messages.add(new DeliveryManager.ChatMessage(message.getString("Sender"),
                    message.getString("SimNet"), message.getString("Text"), message.getLong("Time")));
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (DeliveryManager.ChatMessage message : this.messages) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Sender", message.senderName());
            entry.putString("SimNet", message.senderSimNet());
            entry.putString("Text", message.text());
            entry.putLong("Time", message.timestampMs());
            entries.add(entry);
        }
        tag.put("Messages", entries);
        return tag;
    }

    synchronized void addMessage(DeliveryManager.ChatMessage message) {
        this.messages.add(message);
        while (this.messages.size() > 50) {
            this.messages.removeFirst();
        }
        this.setDirty();
    }

    synchronized List<DeliveryManager.ChatMessage> getMessagesSnapshot() {
        return List.copyOf(this.messages);
    }
}
