package fr.nocsy.mcpets.data;

import lombok.Getter;

public enum PetAIMode {

    FOLLOW("follow"),
    SIT("sit"),
    WANDER("wander");

    @Getter
    private final String mode;

    PetAIMode(String mode) {
        this.mode = mode;
    }

    public boolean equals(PetAIMode mode) {
        return this.getMode().equals(mode.getMode());
    }
}
