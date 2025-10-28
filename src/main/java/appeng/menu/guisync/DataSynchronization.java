/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.menu.guisync;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.AbstractContainerMenu;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;

import appeng.core.AELog;

/**
 * Helper class for synchronizing fields from server-side menus to client-side menus. Fields need to be annotated with
 * {@link GuiSync} and given a unique key within the class hierarchy.
 */
public class DataSynchronization {

    private static final Map<Class<?>, Short2ObjectOpenHashMap<SynchronizedField.Factory>> CACHE = new ConcurrentHashMap<>();

    private final Short2ObjectOpenHashMap<SynchronizedField<?>> fields;

    public DataSynchronization(Object host) {
        var map = collectFields(host.getClass());
        fields = new Short2ObjectOpenHashMap<>(map.size());
        map.short2ObjectEntrySet().fastForEach(
                entry -> fields.put(entry.getShortKey(), entry.getValue().create(host)));
    }

    private static Short2ObjectOpenHashMap<SynchronizedField.Factory> collectFields(Class<?> clazz) {
        return CACHE.computeIfAbsent(clazz, k -> {
            var fields = new Short2ObjectOpenHashMap<SynchronizedField.Factory>();
            for (var f : clazz.getDeclaredFields()) {
                if (f.isAnnotationPresent(GuiSync.class)) {
                    var annotation = f.getAnnotation(GuiSync.class);
                    short key = annotation.value();
                    if (fields.containsKey(key)) {
                        throw new IllegalStateException(
                                "Class " + clazz + " declares the same sync id twice: " + key);
                    }
                    fields.put(key, new SynchronizedField.Factory(f));
                }
            }

            // Recurse upwards through the class hierarchy
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != AbstractContainerMenu.class && superclass != Object.class) {
                if (fields.isEmpty()) {
                    fields = collectFields(superclass);
                } else {
                    fields.putAll(collectFields(superclass));
                }
            }
            return fields;
        });
    }

    public boolean hasChanges() {
        for (SynchronizedField<?> value : fields.values()) {
            if (value.hasChanges()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Write the data for all fields to the given buffer, and marks all fields as unchanged.
     */
    public void writeFull(FriendlyByteBuf data) {
        writeFields(data, true);
    }

    /**
     * Write the data for changed fields to the given buffer, and marks all fields as unchanged.
     */
    public void writeUpdate(FriendlyByteBuf data) {
        writeFields(data, false);
    }

    private void writeFields(FriendlyByteBuf data, boolean includeUnchanged) {
        fields.short2ObjectEntrySet().fastForEach(entry -> {
            if (includeUnchanged || entry.getValue().hasChanges()) {
                data.writeShort(entry.getShortKey());
                entry.getValue().write(data);
            }
        });

        // Terminator
        data.writeVarInt(-1);
    }

    public void readUpdate(FriendlyByteBuf data) {
        for (short key = data.readShort(); key != -1; key = data.readShort()) {
            SynchronizedField<?> field = fields.get(key);
            if (field == null) {
                AELog.warn("Server sent update for GUI field %d, which we don't know.", key);
                continue;
            }

            field.read(data);
        }
    }

    /**
     * @return True if any synchronized fields exist.
     */
    public boolean hasFields() {
        return !fields.isEmpty();
    }
}
