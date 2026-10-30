package me.foesio.foKillstreak.api.event;

import me.foesio.foKillstreak.api.ChangeCause;
import me.foesio.foKillstreak.api.StreakSnapshot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after a stored streak value changes and the SQLite save succeeds. */
public final class KillstreakChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final StreakSnapshot previous;
    private final StreakSnapshot current;
    private final ChangeCause cause;
    private final UUID otherPlayer;

    public KillstreakChangeEvent(boolean asynchronous, StreakSnapshot previous, StreakSnapshot current,
                                 ChangeCause cause, UUID otherPlayer) {
        super(asynchronous);
        this.previous = previous;
        this.current = current;
        this.cause = cause;
        this.otherPlayer = otherPlayer;
    }

    /** Null when this player had no stored record before the change. */
    public StreakSnapshot getPrevious() { return previous; }
    public StreakSnapshot getCurrent() { return current; }
    public ChangeCause getCause() { return cause; }
    /** Killer for DEATH, victim for KILL, otherwise null. */
    public UUID getOtherPlayer() { return otherPlayer; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
