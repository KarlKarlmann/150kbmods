package net.kb150.survivorcolonies.data.dialog;

public record PlayerOption(
    int optionId,
    int textId,
    String stance,
    int trustDelta,
    boolean once
) {}