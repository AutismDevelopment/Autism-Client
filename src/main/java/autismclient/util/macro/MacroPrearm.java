package autismclient.util.macro;

import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismPacketNamer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

import java.lang.reflect.RecordComponent;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class MacroPrearm {
    private MacroPrearm() {
    }
    public static CompletableFuture<Void> predicateFuture(java.util.function.BooleanSupplier predicate, int timeoutMs) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        Thread worker = new Thread(() -> MacroLifecycle.withRunContext(run, () -> {
            long start = System.currentTimeMillis();
            while (!future.isDone() && MacroLifecycle.isCurrentRunActive()) {
                try {
                    if (predicate.getAsBoolean()) {
                        future.complete(null);
                        return;
                    }
                } catch (Throwable ignored) {  }
                if (timeoutMs > 0 && System.currentTimeMillis() - start >= timeoutMs) {
                    future.complete(null);
                    return;
                }
                java.util.concurrent.locks.LockSupport.parkNanos(MacroExecutor.MACRO_WAIT_POLL_NANOS);
                if (Thread.interrupted()) return;
            }
        }), "MacroPredicateWait");
        worker.setDaemon(true);
        worker.start();
        future.whenComplete((v, e) -> worker.interrupt());
        return future;
    }

    public static void prearmNextActionWait(Minecraft mc, java.util.List<MacroAction> actions, int currentIndex) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run == null || actions == null) return;
        int nextIndex = currentIndex + 1;
        if (nextIndex < 0 || nextIndex >= actions.size()) return;
        MacroAction next = actions.get(nextIndex);
        if (next == null || !next.isEnabled() || !next.listensDuringPreviousAction()) return;
        if (!MacroDynamicBindings.apply(next, mc)) return;
        if (!MacroActionRunner.dynamicTextInputsAvailable(next, mc)) return;
        if (!isWaitConditionAction(next)) return;
        synchronized (run.prearmedWaits) {
            if (run.prearmedWaits.containsKey(next)) return;
            clearCaptureOutputs(next);
            clearOwnCaptureOutput(next);
            MacroExecutor.PrearmedWait wait = createPrearmedWait(mc, next);
            if (wait != null) run.prearmedWaits.put(next, wait);
        }
    }

    private static boolean isWaitConditionAction(MacroAction action) {
        return action instanceof WaitForPacketAction
            || action instanceof WaitPacketMatchAction
            || action instanceof WaitForChatAction
            || action instanceof WaitForGuiAction
            || action instanceof WaitGuiTypeAction
            || action instanceof WaitForSoundAction
            || action instanceof WaitForSlotChangeAction
            || action instanceof WaitInventoryPredicateAction
            || action instanceof WaitDurabilityAction
            || action instanceof WaitFreeSlotsAction
            || action instanceof WaitForHealthAction
            || action instanceof WaitForBlockAction
            || action instanceof WaitForEntityAction
            || action instanceof WaitEntityTargetAction
            || action instanceof WaitForCooldownAction
            || action instanceof WaitPosAction
            || action instanceof WaitForPositionDeltaAction
            || action instanceof WaitForTeleportAction
            || action instanceof WaitGamemodeChangeAction
            || action instanceof WaitForWorldChangeAction
            || action instanceof WaitMovementAction
            || action instanceof WaitForLanStepAction
            || action instanceof WaitForMacroStepAction;
    }

    public static MacroExecutor.PrearmedWait consumePrearmedWait(MacroAction action) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run == null || action == null) return null;
        synchronized (run.prearmedWaits) {
            return run.prearmedWaits.remove(action);
        }
    }

    public static boolean waitForPrearmed(MacroAction action, long timeoutMs) {
        MacroExecutor.PrearmedWait wait = consumePrearmedWait(action);
        if (wait == null) return false;
        try {
            MacroRace.waitForFutureDone(wait.future, timeoutMs);
            commitStagedCapture(action);
            return true;
        } finally {
            wait.cleanup();
        }
    }

    private static MacroExecutor.PrearmedWait createPrearmedWait(Minecraft mc, MacroAction action) {
        if (action instanceof WaitForPacketAction packetAction) {
            java.util.List<String> targets = packetAction.effectiveList();
            String firstTarget = targets.isEmpty() ? "" : targets.get(0);
            return packetTargetFuture(packetAction, firstTarget, 1);
        }
        if (action instanceof WaitPacketMatchAction packetMatch) {
            return packetMatchFuture(packetMatch);
        }
        if (action instanceof WaitInventoryPredicateAction inv) {
            inv.resetBaseline();
            return predicatePrearm(action, () -> inv.matches(mc), inv.timeoutMs);
        }
        if (action instanceof WaitDurabilityAction durability) {
            if (durability.useNext) return null;
            return predicatePrearm(action, () -> MacroDurability.durabilityObservationMatches(mc, durability), durability.timeoutMs);
        }
        if (action instanceof WaitFreeSlotsAction freeSlots) {
            return predicatePrearm(action, () -> freeSlots.matches(mc), freeSlots.timeoutMs);
        }
        if (action instanceof WaitEntityTargetAction entityTarget) {
            return predicatePrearm(action, () -> entityTarget.matches(mc), entityTarget.timeoutMs);
        }
        if (action instanceof WaitGuiTypeAction guiType) {
            return predicatePrearm(action,
                guiType.waitMode == WaitGuiTypeAction.WaitMode.CLOSE
                    ? MacroRunLoop.guiClosePredicate(guiType, mc)
                    : () -> {
                        var capture = guiType.capture(mc);
                        capture.ifPresent(values -> stageCapture(guiType, values));
                        return capture.isPresent();
                    },
                guiType.timeoutMs);
        }
        if (action instanceof WaitForChatAction chat) {
            MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
            MacroExecutor.OneShotPacketListener listener = MacroExecutor.awaitReceive(packet -> {
                MacroExecutor.ChatCapture capture = MacroChatObserve.extractIncomingChat(packet);
                var matched = MacroChatObserve.captureChatAction(chat, capture, run == null ? null : run.variables);
                if (matched.isEmpty()) return false;
                if (run != null) run.stagedCaptures.put(chat, matched.get());
                return true;
            });
            CompletableFuture<?> future = listener.future;
            if (chat.timeoutMs > 0) future.completeOnTimeout(null, chat.timeoutMs, TimeUnit.MILLISECONDS);
            return new MacroExecutor.PrearmedWait(action, future, listener::cancel, 0);
        }
        if (action instanceof WaitForMacroStepAction macroStep) {
            return predicatePrearm(action, () -> MacroRace.isMacroStepSatisfied(macroStep), macroStep.timeoutMs);
        }
        if (action instanceof WaitForLanStepAction lanStep) {
            CompletableFuture<Void> future = MacroConditionRegistry.waitForLanStep(lanStep);
            return new MacroExecutor.PrearmedWait(action, future, () -> {
                if (!future.isDone()) future.cancel(true);
            }, 0);
        }
        CompletableFuture<?> future = MacroRace.raceConditionFuture(mc, action);
        if (future == null) return null;
        int timeout = action instanceof WaitForChatAction chat ? chat.timeoutMs : 0;
        if (timeout > 0) future.completeOnTimeout(null, timeout, TimeUnit.MILLISECONDS);
        return new MacroExecutor.PrearmedWait(action, future, () -> {
            if (!future.isDone()) future.cancel(true);
        }, 0);
    }

    public static boolean hasPrearmedWait(MacroAction action) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run == null || action == null) return false;
        synchronized (run.prearmedWaits) {
            return run.prearmedWaits.containsKey(action);
        }
    }

    public static void recordMatchedPacket(MacroAction action, Packet<?> packet, String direction) {
        if (action instanceof WaitForPacketAction wait) {
            wait.matchedPacket = packet;
            wait.matchedDirection = direction == null ? "" : direction;
        } else if (action instanceof WaitPacketMatchAction wait) {
            wait.matchedPacket = packet;
            wait.matchedDirection = direction == null ? "" : direction;
        }
    }

    public static void copyLastMatchedPacket(WaitForPacketAction action) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (action == null || run == null || run.lastMatchedPacket == null) return;
        action.matchedPacket = run.lastMatchedPacket;
        action.matchedDirection = run.lastMatchedPacketDirection;
    }

    static void clearCaptureOutputs(MacroAction action) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run == null || action == null) return;
        run.stagedCaptures.remove(action);
        if (action instanceof WaitForChatAction chat) {
            for (String name : MacroCapturePattern.declaredNames(chat.effectiveMatchMode(), chat.pattern)) run.variables.remove(name);
            if (chat.saveAs != null && !chat.saveAs.isBlank()) run.variables.remove(chat.saveAs);
        } else if (action instanceof WaitForGuiAction gui) {
            for (String name : MacroCapturePattern.declaredNames(gui.matchMode, gui.guiTitle)) run.variables.remove(name);
            if (gui.saveAs != null && !gui.saveAs.isBlank()) run.variables.remove(gui.saveAs);
        } else if (action instanceof WaitGuiTypeAction gui) {
            for (String name : MacroCapturePattern.declaredNames(gui.matchMode, gui.title)) run.variables.remove(name);
            if (gui.saveAs != null && !gui.saveAs.isBlank()) run.variables.remove(gui.saveAs);
        } else if (action instanceof CaptureValueAction capture) {
            for (String name : MacroCapturePattern.declaredNames(capture.matchMode, capture.pattern)) run.variables.remove(name);
            if (capture.saveAs != null && !capture.saveAs.isBlank()) run.variables.remove(capture.saveAs);
        }
    }

    public static void clearOwnCaptureOutput(MacroAction action) {
        if (!(action instanceof MacroCaptureOutput output)) return;
        String name = output.getSaveAs();
        if (name != null && !name.isBlank()) MacroVariables.remove(name);
        if (action instanceof WaitForSoundAction sound) sound.matchedSoundId = "";
        if (action instanceof WaitForPacketAction packet) {
            packet.matchedPacket = null;
            packet.matchedDirection = "";
        }
        if (action instanceof WaitPacketMatchAction packet) {
            packet.matchedPacket = null;
            packet.matchedDirection = "";
        }
    }

    public static void commitConditionOutput(MacroAction action, Minecraft mc) throws InterruptedException {
        if (!(action instanceof MacroCaptureOutput output)) return;
        String name = MacroVariableContext.cleanRootName(output.getSaveAs());
        if (name.isEmpty()) return;
        MacroValue value = MacroClientThread.callOnClientThread(mc, () -> conditionOutputValue(action, mc), null);
        if (value != null) MacroVariables.setValue(name, value);
    }

    public static MacroValue conditionOutputValue(MacroAction action, Minecraft mc) {
        if (action instanceof WaitForHealthAction healthAction && mc != null && mc.player != null) {
            float health = mc.player.getHealth();
            if (healthAction.below ? health >= healthAction.healthThreshold : health <= healthAction.healthThreshold) return null;
            return MacroValue.structured(MacroValue.Kind.NUMBER, Float.toString(health), java.util.Map.of(
                "health", MacroValue.number(health),
                "max", MacroValue.number(mc.player.getMaxHealth()),
                "absorption", MacroValue.number(mc.player.getAbsorptionAmount())
            ));
        }
        if (action instanceof WaitForEntityAction entityAction) {
            net.minecraft.world.entity.Entity entity = entityAction.findMatchingEntity(mc);
            if (entity == null) return null;
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            double distance = mc != null && mc.player != null ? mc.player.distanceTo(entity) : 0.0;
            return MacroValue.structured(MacroValue.Kind.ENTITY, entity.getName().getString(), java.util.Map.of(
                "name", MacroValue.text(entity.getName().getString()),
                "id", MacroValue.identifier(id),
                "uuid", MacroValue.text(entity.getStringUUID()),
                "x", MacroValue.number(entity.getX()),
                "y", MacroValue.number(entity.getY()),
                "z", MacroValue.number(entity.getZ()),
                "distance", MacroValue.number(distance)
            ));
        }
        if (action instanceof WaitForSoundAction sound) {
            if (sound.matchedSoundId == null || sound.matchedSoundId.isBlank()) return null;
            return MacroValue.structured(MacroValue.Kind.IDENTIFIER, sound.matchedSoundId, java.util.Map.of(
                "id", MacroValue.identifier(sound.matchedSoundId),
                "x", MacroValue.number(sound.matchedX),
                "y", MacroValue.number(sound.matchedY),
                "z", MacroValue.number(sound.matchedZ)
            ));
        }
        if (action instanceof WaitForPacketAction packet) {
            return packetMacroValue(packet.matchedPacket, packet.matchedDirection);
        }
        if (action instanceof WaitPacketMatchAction packet) {
            return packetMacroValue(packet.matchedPacket, packet.matchedDirection);
        }
        if (action instanceof WaitInventoryPredicateAction inventory) {
            if (!inventory.matches(mc)) return null;
            return inventoryMacroValue(mc, inventory.itemName, inventory.slot, inventory.condition.name());
        }
        if (action instanceof WaitForSlotChangeAction slotWait) {
            if (slotWait.entries.isEmpty()) return inventoryMacroValue(mc, "", -1, "slot_change");
            ItemTarget target = slotWait.entries.get(0).resolvedTarget().resolveTemplate(mc);
            return target == null ? null : itemTargetMacroValue(mc, target);
        }
        if (action instanceof WaitPosAction || action instanceof WaitForTeleportAction
                || action instanceof WaitForPositionDeltaAction) {
            return playerPositionMacroValue(mc);
        }
        if (action instanceof WaitForWorldChangeAction && mc != null && mc.level != null) {
            String dimension = mc.level.dimension().identifier().toString();
            return MacroValue.structured(MacroValue.Kind.IDENTIFIER, dimension, java.util.Map.of(
                "id", MacroValue.identifier(dimension), "name", MacroValue.text(dimension)));
        }
        if (action instanceof WaitGamemodeChangeAction && mc != null && mc.gameMode != null) {
            String mode = mc.gameMode.getPlayerMode().getName();
            WaitGamemodeChangeAction wait = (WaitGamemodeChangeAction) action;
            if (wait.match == WaitGamemodeChangeAction.Match.TO_MODE && !wait.accepts(mc.gameMode.getPlayerMode())) return null;
            return MacroValue.structured(MacroValue.Kind.TEXT, mode, java.util.Map.of("name", MacroValue.text(mode)));
        }
        return null;
    }

    private static MacroValue playerPositionMacroValue(Minecraft mc) {
        if (mc == null || mc.player == null) return null;
        String display = String.format(Locale.ROOT, "%.3f, %.3f, %.3f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
        return MacroValue.structured(MacroValue.Kind.POSITION, display, java.util.Map.of(
            "x", MacroValue.number(mc.player.getX()), "y", MacroValue.number(mc.player.getY()),
            "z", MacroValue.number(mc.player.getZ()), "yaw", MacroValue.number(mc.player.getYRot()),
            "pitch", MacroValue.number(mc.player.getXRot())
        ));
    }

    private static MacroValue inventoryMacroValue(Minecraft mc, String itemTemplate, int slot, String condition) {
        if (mc == null || mc.player == null) return null;
        MacroTemplate.Resolution item = MacroVariables.resolve(itemTemplate, mc);
        if (!item.success()) return null;
        ItemTarget target = ItemTarget.fromLegacyEntry(item.value());
        if (slot >= 0) {
            int safeSlot = Math.max(0, Math.min(mc.player.getInventory().getContainerSize() - 1, slot));
            MacroValue stack = stackMacroValue(mc.player.getInventory().getItem(safeSlot), safeSlot);
            return stack == null ? MacroValue.structured(MacroValue.Kind.SLOT, Integer.toString(slot), java.util.Map.of(
                "slot", MacroValue.slot(slot), "empty", MacroValue.text("true"), "condition", MacroValue.text(condition))) : stack;
        }
        if (target.hasIdentity()) {
            MacroValue matched = itemTargetMacroValue(mc, target);
            if (matched != null) return matched.withProperty("condition", MacroValue.text(condition));
        }
        return MacroValue.structured(MacroValue.Kind.TEXT, condition, java.util.Map.of(
            "condition", MacroValue.text(condition),
            "selected_slot", MacroValue.slot(mc.player.getInventory().getSelectedSlot())
        ));
    }

    private static MacroValue itemTargetMacroValue(Minecraft mc, ItemTarget target) {
        if (mc == null || mc.player == null || target == null) return null;
        if (mc.player.containerMenu != null) {
            for (Slot slot : mc.player.containerMenu.slots) {
                if (slot == null || slot.getItem().isEmpty()) continue;
                int visible = AutismInventoryHelper.toUserVisibleSlot(mc, slot.index);
                if (target.score(slot.getItem(), visible) >= 0) return stackMacroValue(slot.getItem(), visible);
            }
        }
        return null;
    }

    private static MacroValue stackMacroValue(ItemStack stack, int slot) {
        if (stack == null || stack.isEmpty()) return null;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemId = id == null ? "" : id.toString();
        String name = stack.getHoverName().getString();
        return MacroValue.structured(MacroValue.Kind.ITEM, name, java.util.Map.of(
            "name", MacroValue.text(name), "id", MacroValue.identifier(itemId),
            "count", MacroValue.number(stack.getCount()), "slot", MacroValue.slot(slot)
        ));
    }

    private static MacroValue packetMacroValue(Packet<?> packet, String direction) {
        if (packet == null) return null;
        java.util.LinkedHashMap<String, MacroValue> properties = new java.util.LinkedHashMap<>();
        String packetName = AutismPacketNamer.getFriendlyName(packet, direction);
        properties.put("name", MacroValue.text(packetName));
        properties.put("class", MacroValue.text(packet.getClass().getName()));
        properties.put("direction", MacroValue.text(direction));
        RecordComponent[] components = packet.getClass().getRecordComponents();
        if (components != null) {
            for (RecordComponent component : components) {
                if (properties.size() >= 64) break;
                try {
                    Object value = component.getAccessor().invoke(packet);
                    properties.put(component.getName().toLowerCase(Locale.ROOT), MacroValue.text(value));
                } catch (ReflectiveOperationException ignored) {  }
            }
        }
        return MacroValue.structured(MacroValue.Kind.PACKET, packetName, properties);
    }

    public static void stageCapture(MacroAction action, java.util.Map<String, MacroValue> values) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run == null || action == null || values == null) return;
        run.stagedCaptures.put(action, java.util.Map.copyOf(values));
    }

    static void commitStagedCapture(MacroAction action) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run == null || action == null) return;
        java.util.Map<String, MacroValue> values = run.stagedCaptures.remove(action);
        if (values != null) run.variables.setAll(values);
    }

    private static MacroExecutor.PrearmedWait predicatePrearm(MacroAction action, java.util.function.BooleanSupplier predicate, int timeoutMs) {
        java.util.concurrent.atomic.AtomicBoolean active = new java.util.concurrent.atomic.AtomicBoolean(true);
        CompletableFuture<Void> future = new CompletableFuture<>();
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        Thread worker = new Thread(() -> MacroLifecycle.withRunContext(run, () -> {
            long start = System.currentTimeMillis();
            while (active.get() && MacroLifecycle.isCurrentRunActive() && !future.isDone()) {
                try {
                    if (predicate.getAsBoolean()) {
                        future.complete(null);
                        return;
                    }
                } catch (Throwable ignored) {  }
                if (timeoutMs > 0 && System.currentTimeMillis() - start >= timeoutMs) {
                    future.complete(null);
                    return;
                }
                java.util.concurrent.locks.LockSupport.parkNanos(MacroExecutor.MACRO_WAIT_POLL_NANOS);
                if (Thread.interrupted()) return;
            }
        }), "MacroPrearmedWait");
        worker.setDaemon(true);
        worker.start();
        return new MacroExecutor.PrearmedWait(action, future, () -> {
            active.set(false);
            if (!future.isDone()) future.cancel(true);
            worker.interrupt();
        }, 0);
    }

    private static MacroExecutor.PrearmedWait packetTargetFuture(MacroAction action, String target, int completedPacketTargets) {
        String normalizedTarget = WaitForPacketAction.normalizeTarget(target);
        String direction = WaitForPacketAction.getDirection(normalizedTarget);
        CompletableFuture<Packet<?>> matched = new CompletableFuture<>();
        MacroExecutor.OneShotPacketListener send = null;
        MacroExecutor.OneShotPacketListener recv = null;
        if (!"S2C".equalsIgnoreCase(direction)) {
            send = MacroExecutor.awaitSend(packet -> MacroPacketWait.matchesPacketTarget(normalizedTarget, packet, "C2S"));
            send.future.thenAccept(packet -> {
                recordMatchedPacket(action, packet, "C2S");
                matched.complete(packet);
            });
        }
        if (!"C2S".equalsIgnoreCase(direction)) {
            recv = MacroExecutor.awaitReceive(packet -> MacroPacketWait.matchesPacketTarget(normalizedTarget, packet, "S2C"));
            recv.future.thenAccept(packet -> {
                recordMatchedPacket(action, packet, "S2C");
                matched.complete(packet);
            });
        }
        MacroExecutor.OneShotPacketListener finalSend = send;
        MacroExecutor.OneShotPacketListener finalRecv = recv;
        return new MacroExecutor.PrearmedWait(action, matched, () -> {
            if (finalSend != null) finalSend.cancel();
            if (finalRecv != null) finalRecv.cancel();
            if (!matched.isDone()) matched.cancel(true);
        }, completedPacketTargets);
    }

    private static MacroExecutor.PrearmedWait packetMatchFuture(WaitPacketMatchAction action) {
        MacroExecutor.OneShotPacketListener send = null;
        MacroExecutor.OneShotPacketListener recv = null;
        CompletableFuture<Packet<?>> matched = new CompletableFuture<>();
        if (action.direction != WaitPacketMatchAction.Direction.S2C) {
            send = MacroExecutor.awaitSend(packet -> action.matches(packet, "C2S"));
            send.future.thenAccept(packet -> {
                recordMatchedPacket(action, packet, "C2S");
                matched.complete(packet);
            });
        }
        if (action.direction != WaitPacketMatchAction.Direction.C2S) {
            recv = MacroExecutor.awaitReceive(packet -> action.matches(packet, "S2C"));
            recv.future.thenAccept(packet -> {
                recordMatchedPacket(action, packet, "S2C");
                matched.complete(packet);
            });
        }
        if (send == null && recv == null) return null;
        if (action.timeoutMs > 0) matched.completeOnTimeout(null, action.timeoutMs, TimeUnit.MILLISECONDS);
        MacroExecutor.OneShotPacketListener finalSend = send;
        MacroExecutor.OneShotPacketListener finalRecv = recv;
        return new MacroExecutor.PrearmedWait(action, matched, () -> {
            if (finalSend != null) finalSend.cancel();
            if (finalRecv != null) finalRecv.cancel();
            if (!matched.isDone()) matched.cancel(true);
        }, 0);
    }

    public static void waitForPacketMatch(WaitPacketMatchAction action) {
        if (action == null) return;
        MacroExecutor.OneShotPacketListener send = null;
        MacroExecutor.OneShotPacketListener recv = null;
        try {
            if (action.direction != WaitPacketMatchAction.Direction.S2C) {
                send = MacroExecutor.awaitSend(packet -> {
                    boolean matches = action.matches(packet, "C2S");
                    if (matches) recordMatchedPacket(action, packet, "C2S");
                    return matches;
                });
            }
            if (action.direction != WaitPacketMatchAction.Direction.C2S) {
                recv = MacroExecutor.awaitReceive(packet -> {
                    boolean matches = action.matches(packet, "S2C");
                    if (matches) recordMatchedPacket(action, packet, "S2C");
                    return matches;
                });
            }
            CompletableFuture<?> future;
            if (send != null && recv != null) future = CompletableFuture.anyOf(send.future, recv.future);
            else if (send != null) future = send.future;
            else if (recv != null) future = recv.future;
            else return;
            if (action.timeoutMs > 0) future.completeOnTimeout(null, action.timeoutMs, TimeUnit.MILLISECONDS);
            MacroRace.waitForFutureDone(future, action.timeoutMs);
        } finally {
            if (send != null) send.cancel();
            if (recv != null) recv.cancel();
        }
    }
}
