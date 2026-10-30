package me.foesio.foKillstreak.listener;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KillCreditCooldownsTest {
    @Test
    void fullTrackerDeniesNewCreditAndFailedSaveCanReleaseReservation() {
        KillCreditCooldowns cooldowns = new KillCreditCooldowns(1, 60_000);
        UUID killer = UUID.randomUUID();
        UUID firstVictim = UUID.randomUUID();
        UUID secondVictim = UUID.randomUUID();

        KillCreditCooldowns.Reservation first = cooldowns.reserve(killer, firstVictim, 60, 1_000);
        assertEquals(KillCreditCooldowns.Status.RESERVED, first.status());
        assertEquals(KillCreditCooldowns.Status.COOLDOWN,
                cooldowns.reserve(killer, firstVictim, 60, 1_100).status());
        assertEquals(KillCreditCooldowns.Status.CAPACITY_REACHED,
                cooldowns.reserve(killer, secondVictim, 60, 1_100).status());

        cooldowns.release(first);
        assertEquals(0, cooldowns.remainingSeconds(killer, firstVictim, 1_100));
        assertEquals(KillCreditCooldowns.Status.RESERVED,
                cooldowns.reserve(killer, secondVictim, 60, 1_100).status());
        assertEquals(60, cooldowns.remainingSeconds(killer, secondVictim, 1_100));
    }

    @Test
    void expiryAndQuitClearTrackedPair() {
        KillCreditCooldowns cooldowns = new KillCreditCooldowns(1, 60_000);
        UUID killer = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        UUID otherVictim = UUID.randomUUID();

        cooldowns.reserve(killer, victim, 1, 1_000);
        assertEquals(1, cooldowns.remainingSeconds(killer, victim, 1_999));
        assertEquals(0, cooldowns.remainingSeconds(killer, victim, 2_000));
        assertEquals(KillCreditCooldowns.Status.RESERVED,
                cooldowns.reserve(killer, otherVictim, 1, 2_000).status());
        cooldowns.clear(otherVictim);
        assertEquals(0, cooldowns.remainingSeconds(killer, otherVictim, 2_100));
        assertEquals(KillCreditCooldowns.Status.NOT_REQUIRED,
                cooldowns.reserve(killer, victim, 0, 2_100).status());
    }
}
