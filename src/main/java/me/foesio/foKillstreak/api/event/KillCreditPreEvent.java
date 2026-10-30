package me.foesio.foKillstreak.api.event;

import me.foesio.foKillstreak.api.StreakSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after the initial cooldown check, before credit, rewards or milestone broadcasts. */
public final class KillCreditPreEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player killer;
    private final Player victim;
    private final StreakSnapshot previous;
    private boolean cancelled;

    public KillCreditPreEvent(boolean asynchronous, Player killer, Player victim, StreakSnapshot previous) {
        super(asynchronous);
        this.killer = killer;
        this.victim = victim;
        this.previous = previous;
    }

    public Player getKiller() { return killer; }
    public Player getVictim() { return victim; }
    /** Null when the killer has no stored record. */
    public StreakSnapshot getPrevious() { return previous; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
