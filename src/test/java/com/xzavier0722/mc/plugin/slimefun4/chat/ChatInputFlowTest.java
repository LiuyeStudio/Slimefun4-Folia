package com.xzavier0722.mc.plugin.slimefun4.chat;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Regression tests for the chat input flow used by the guide search, the GPS waypoint naming and
 * the android script editor.
 * <p>
 * These used to rely on dough's {@code ChatInput}, whose listener is a static singleton that is only
 * ever bound to the first plugin instance. That singleton goes stale on a plugin reload, after which
 * the callback is never invoked and the typed message is simply sent as a normal chat message.
 * <p>
 * {@code PlayerChatCatcher} takes a final plugin type, so this test wires up an equivalent
 * catcher/listener pair to exercise the same flow.
 */
class ChatInputFlowTest {

    private ServerMock server;
    private TestPlugin plugin;
    private ConcurrentHashMap<UUID, java.util.function.Consumer<String>> catchers;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(TestPlugin.class);
        catchers = new ConcurrentHashMap<>();

        // Mirrors PlayerChatListener exactly
        server.getPluginManager()
                .registerEvents(
                        new Listener() {
                            @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
                            public void onChat(AsyncPlayerChatEvent e) {
                                var handler = catchers.remove(e.getPlayer().getUniqueId());
                                if (handler != null) {
                                    e.setCancelled(true);
                                    handler.accept(e.getMessage());
                                }
                            }
                        },
                        plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void catcherReceivesTypedMessage() {
        PlayerMock player = server.addPlayer();

        AtomicBoolean called = new AtomicBoolean(false);
        StringBuilder received = new StringBuilder();
        catchers.put(player.getUniqueId(), message -> {
            called.set(true);
            received.append(message);
        });

        player.chat("slime");
        awaitAsyncEvents();

        Assertions.assertTrue(called.get(), "The chat catcher never received the typed message");
        Assertions.assertEquals("slime", received.toString());
    }

    @Test
    void catcherIsConsumedAfterUse() {
        PlayerMock player = server.addPlayer();

        catchers.put(player.getUniqueId(), message -> {});
        player.chat("slime");
        awaitAsyncEvents();

        Assertions.assertTrue(catchers.isEmpty(), "The catcher should be consumed after use");
    }

    @Test
    void catcherIsBoundToTheSchedulingPlayer() {
        PlayerMock alice = server.addPlayer("alice");
        PlayerMock bob = server.addPlayer("bob");

        ConcurrentHashMap<UUID, String> seen = new ConcurrentHashMap<>();
        catchers.put(alice.getUniqueId(), message -> seen.put(alice.getUniqueId(), message));
        catchers.put(bob.getUniqueId(), message -> seen.put(bob.getUniqueId(), message));

        bob.chat("bob-message");
        awaitAsyncEvents();

        Assertions.assertTrue(seen.containsKey(bob.getUniqueId()), "Bob's catcher should have fired");
        Assertions.assertFalse(seen.containsKey(alice.getUniqueId()), "Alice's catcher must not fire for Bob");
        Assertions.assertTrue(catchers.containsKey(alice.getUniqueId()), "Alice's catcher should still be waiting");
    }

    /**
     * Chat events are dispatched asynchronously, so wait for them and then let the pending
     * synchronous tasks (the catcher callbacks) run.
     */
    private void awaitAsyncEvents() {
        server.getScheduler().waitAsyncEventsFinished();
        Assertions.assertDoesNotThrow(() -> server.getScheduler().performTicks(2L));
    }

    public static class TestPlugin extends org.bukkit.plugin.java.JavaPlugin {}
}
