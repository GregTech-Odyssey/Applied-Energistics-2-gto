/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.hooks.ticking;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.common.base.Stopwatch;

import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent.LevelTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.core.AELog;
import appeng.me.Grid;
import appeng.me.GridNode;
import appeng.me.service.StorageService;
import appeng.util.ILevelRunnable;
import appeng.util.Platform;

public class TickHandler {

    /**
     * Time limit for process queues with respect to the 50ms of a minecraft tick.
     */
    private static final int TIME_LIMIT_PROCESS_QUEUE_MILLISECONDS = 25;

    private static final TickHandler INSTANCE = new TickHandler();
    private final Queue<ILevelRunnable> serverQueue = new ArrayDeque<>();
    private final Map<LevelAccessor, Queue<ILevelRunnable>> callQueue = new HashMap<>();
    private final ServerBlockEntityRepo blockEntities = new ServerBlockEntityRepo();
    private final ServerGridRepo grids = new ServerGridRepo();

    /**
     * A stop watch to limit processing the additional queues to honor
     * {@link TickHandler#TIME_LIMIT_PROCESS_QUEUE_MILLISECONDS}.
     * <p>
     * This cumulative for all queues of one server tick.
     */
    private final Stopwatch stopWatch = Stopwatch.createUnstarted();
    private int processQueueElementsProcessed = 0;
    private int processQueueElementsRemaining = 0;

    private long tickCounter;

    public boolean playerJoined;

    public static TickHandler instance() {
        return INSTANCE;
    }

    private TickHandler() {
    }

    public void init() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onLevelTick);
        MinecraftForge.EVENT_BUS.addListener(this::onUnloadChunk);
        // Try to go last for level unloads since we use it to clean-up state
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onUnloadLevel);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        playerJoined = true;
    }

    public void addCallable(LevelAccessor level, Runnable c) {
        addCallable(level, ignored -> c.run());
    }

    /**
     * Add a server or level callback which gets called the next time the queue is ticked.
     * <p>
     * Callbacks on the client are not support.
     * <p>
     * Using null as level will queue it into the global {@link ServerTickEvent}, otherwise it will be ticked with the
     * corresponding {@link WorldTickEvent}.
     *
     * @param level null or the specific {@link Level}
     * @param c     the callback
     */
    public void addCallable(LevelAccessor level, ILevelRunnable c) {
        if (level == null) {
            this.serverQueue.add(c);
        } else {
            var queue = this.callQueue.computeIfAbsent(level, k -> new ArrayDeque<>());
            queue.add(c);
        }
    }

    /**
     * Add a {@link AEBaseBlockEntity} to be initializes with the next update.
     *
     * @see appeng.api.networking.GridHelper#onFirstTick
     */
    public <T extends BlockEntity> void addInit(T blockEntity, Consumer<? super T> initFunction) {
        Objects.requireNonNull(blockEntity);

        // for no there is no reason to care about this on the client...
        if (!blockEntity.getLevel().isClientSide()) {
            this.blockEntities.addBlockEntity(blockEntity, initFunction);
        }
    }

    /**
     * Add a new grid for ticking on the next update.
     * <p>
     * Must only be called on the server.
     *
     * @param grid the {@link Grid} to add, must be not null
     */
    public void addNetwork(Grid grid) {
        this.grids.addNetwork(grid);
    }

    /**
     * Mark a {@link Grid} to be removed with the next update.
     * <p>
     * Must only be called on the server.
     *
     * @param grid the {@link Grid} to remove, must be not null
     */
    public void removeNetwork(Grid grid) {
        this.grids.removeNetwork(grid);
    }

    public Iterable<Grid> getGridList() {
        return this.grids.networks;
    }

    public void shutdown() {
        Platform.assertServerThread();
        playerJoined = false;
        this.blockEntities.clear();
        this.grids.clear();
    }

    /**
     * Handles a chunk being unloaded (on the server)
     * <p>
     * Removes any pending initialization callbacks for block entities in that chunk.
     */
    public void onUnloadChunk(final ChunkEvent.Unload ev) {
        var level = ev.getLevel();
        var chunk = ev.getChunk();

        if (!level.isClientSide()) {
            this.blockEntities.removeChunk(level, chunk.getPos().toLong());
        }
    }

    /**
     * Handle a level unload and tear down related data structures.
     */
    public void onUnloadLevel(final LevelEvent.Unload ev) {
        var level = ev.getLevel();

        if (level.isClientSide()) {
            return; // for no there is no reason to care about this on the client...
        }

        var toDestroy = new ArrayList<GridNode>();

        this.grids.updateNetworks();
        this.grids.networks.forEach(g -> {
            for (var n : g.getNodes()) {
                if (n.getLevel() == level) {
                    toDestroy.add((GridNode) n);
                }
            }
        });

        for (var n : toDestroy) {
            n.destroy();
        }

        this.blockEntities.removeLevel(level);
        this.callQueue.remove(level);
    }

    /**
     * Tick a single {@link Level}
     * <p>
     * This can happen multiple times per level, but each level should only be ticked once per minecraft tick.
     */
    public void onLevelTick(final LevelTickEvent ev) {
        var level = ev.level;

        if (!(level instanceof ServerLevel serverLevel) || ev.side != LogicalSide.SERVER) {
            // While forge doesn't generate this event for client worlds,
            // the event is generic enough that some other mod might be insane enough to do so.
            return;
        }

        if (ev.phase == Phase.START) {
            onServerLevelTickStart(serverLevel);
        } else if (ev.phase == Phase.END) {
            onServerLevelTickEnd(serverLevel);
        }
    }

    private void onServerLevelTickStart(ServerLevel level) {
        var queue = this.callQueue.remove(level);
        processQueueElementsRemaining += this.processQueue(queue, level);
        var newQueue = this.callQueue.put(level, queue);
        // Some new tasks may have been added while we were processing the queue
        if (newQueue != null) {
            queue.addAll(newQueue);
        }

        // tick networks
        this.grids.lStart.forEach(g -> {
            try {
                g.onLevelStartTick(level);
            } catch (Throwable t) {
                CrashReport crashReport = CrashReport.forThrowable(t, "Ticking grid on start of level tick");
                g.fillCrashReportCategory(crashReport.addCategory("Grid being ticked"));
                level.fillReportDetails(crashReport);
                throw new ReportedException(crashReport);
            }
        });
    }

    private void onServerLevelTickEnd(ServerLevel level) {
        this.readyBlockEntities(level);

        // tick networks
        this.grids.lEnd.forEach(g -> {
            try {
                g.onLevelEndTick(level);
            } catch (Throwable t) {
                CrashReport crashReport = CrashReport.forThrowable(t, "Ticking grid on end of level tick");
                g.fillCrashReportCategory(crashReport.addCategory("Grid being ticked"));
                level.fillReportDetails(crashReport);
                throw new ReportedException(crashReport);
            }
        });
    }

    /**
     * Tick everything related to the global server tick once per minecraft tick.
     */
    public void onServerTick(final ServerTickEvent ev) {
        if (ev.phase == Phase.START) {
            onServerTickStart(ev.getServer());
        } else if (ev.phase == Phase.END) {
            onServerTickEnd(ev.getServer());
        }
    }

    private void onServerTickStart(MinecraftServer server) {
        StorageService.join(server);
        // Reset the stop watch on the start of each server tick.
        this.processQueueElementsProcessed = 0;
        this.processQueueElementsRemaining = 0;
        this.stopWatch.reset();

        // tick networks
        this.grids.updateNetworks();
        this.grids.start.forEach(g -> {
            try {
                g.onServerStartTick();
            } catch (Throwable t) {
                CrashReport crashReport = CrashReport.forThrowable(t, "Ticking grid on start of server tick");
                g.fillCrashReportCategory(crashReport.addCategory("Grid being ticked"));
                throw new ReportedException(crashReport);
            }
        });
    }

    private void onServerTickEnd(MinecraftServer server) {
        // tick networks
        this.grids.end.forEach(g -> {
            try {
                g.onServerEndTick(server);
            } catch (Throwable t) {
                CrashReport crashReport = CrashReport.forThrowable(t, "Ticking grid on end of server tick");
                g.fillCrashReportCategory(crashReport.addCategory("Grid being ticked"));
                throw new ReportedException(crashReport);
            }
        });

        // cross level queue.
        processQueueElementsRemaining += this.processQueue(this.serverQueue, null);

        if (this.stopWatch.elapsed(TimeUnit.MILLISECONDS) > TIME_LIMIT_PROCESS_QUEUE_MILLISECONDS) {
            AELog.warn("Exceeded time limit of %d ms after processing %d queued tick callbacks (%d remain)",
                    TIME_LIMIT_PROCESS_QUEUE_MILLISECONDS, processQueueElementsProcessed,
                    processQueueElementsRemaining);
        }

        tickCounter++;
        StorageService.asyncUpdate();
    }

    /**
     * Ready the block entities in this level. server-side only.
     */
    private void readyBlockEntities(ServerLevel level) {
        var levelQueue = blockEntities.getBlockEntities(level);
        if (levelQueue == null) {
            return;
        }

        // Make a copy because this set may be modified when new chunks are loaded by an onReady call below
        long[] workSet = levelQueue.keySet().toLongArray();

        for (long packedChunkPos : workSet) {
            // Readies all of our block entities in this chunk as soon as it can tick BEs
            // The following test is equivalent to ServerLevel#isPositionTickingWithEntitiesLoaded
            if (Platform.areBlockEntitiesTicking(level, packedChunkPos)) {
                // Take the currently waiting block entities for this chunk and ready them all. Should more block
                // entities be added to this chunk while we're working on it, a new list will be added automatically and
                // we'll work on this chunk again next tick.
                var chunkQueue = levelQueue.remove(packedChunkPos);
                if (chunkQueue == null) {
                    AELog.warn("Chunk %s was unloaded while we were readying block entities",
                            new ChunkPos(packedChunkPos));
                    continue; // This should never happen, chunk unloaded under our noses
                }

                for (var info : chunkQueue) {
                    // Only ready block entities which weren't destroyed in the meantime.
                    if (!info.blockEntity().isRemoved()) {
                        try {
                            // This could load more chunks, but the earliest time to be initialized is the next tick.
                            info.callInit();
                        } catch (Throwable t) {
                            CrashReport crashReport = CrashReport.forThrowable(t, "Readying AE2 block entity");

                            var category = crashReport.addCategory("Block entity being readied");
                            category.setDetail("World", () -> level.dimension().location().toString());
                            info.blockEntity().fillCrashReportCategory(category);

                            throw new ReportedException(crashReport);
                        }
                    }
                }
            }
        }
    }

    /**
     * Process the {@link ILevelRunnable} queue in this {@link Level}
     * <p>
     * This has a hard limit of about 50 ms before deferring further processing into the next tick.
     *
     * @param queue the queue to process
     * @param level the level in which the queue is processed or null for the server queue
     * @return the amount of remaining callbacks
     */
    private int processQueue(Queue<ILevelRunnable> queue, Level level) {
        if (queue == null) {
            return 0;
        }

        // start the clock
        stopWatch.start();

        while (!queue.isEmpty()) {
            try {
                // call the first queue element.
                queue.poll().call(level);
                this.processQueueElementsProcessed++;

                if (stopWatch.elapsed(TimeUnit.MILLISECONDS) > TIME_LIMIT_PROCESS_QUEUE_MILLISECONDS) {
                    break;
                }
            } catch (Exception e) {
                AELog.warn(e);
            }
        }

        // stop watch for the next call
        stopWatch.stop();

        return queue.size();
    }

    public long getCurrentTick() {
        return tickCounter;
    }

    public List<Component> getBlockEntityReport() {
        return blockEntities.getReport();
    }
}
