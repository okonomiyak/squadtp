package uk.iwaservice.squadtp.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * One third-party option shown in {@code RespawnChoiceScreen}, as returned by
 * {@link RespawnChoiceProvider#getChoices}.
 */
public record RespawnChoiceEntry(String choiceId, Component label, ResourceLocation dimension, BlockPos pos) {
}
