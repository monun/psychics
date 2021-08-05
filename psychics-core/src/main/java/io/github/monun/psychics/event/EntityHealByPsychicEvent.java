package io.github.monun.psychics.event;

import io.github.monun.psychics.Ability;
import io.github.monun.psychics.AbilityConcept;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.jetbrains.annotations.NotNull;

public class EntityHealByPsychicEvent extends EntityRegainHealthEvent {
    private final Player healer;

    private final Ability<? extends AbilityConcept> ability;

    public EntityHealByPsychicEvent(
            @NotNull Player healer,
            @NotNull Entity entity,
            double amount,
            @NotNull Ability<? extends AbilityConcept> ability
    ) {
        super(entity, amount, RegainReason.CUSTOM);

        this.healer = healer;
        this.ability = ability;
    }

    public Entity getHealer() {
        return healer;
    }

    @NotNull
    public Ability<? extends AbilityConcept> getAbility() {
        return ability;
    }
}
