package uk.iwaservice.squadtp.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * One third-party option shown in {@code RespawnChoiceScreen}, as returned by
 * {@link RespawnChoiceProvider#getChoices}.
 *
 * @param color  RGB tint for the list swatch and map marker; 0 = default
 * @param pinned pinned entries are listed first, above rally/beacon/members
 */
public record RespawnChoiceEntry(String choiceId, Component label, ResourceLocation dimension, BlockPos pos,
                                 int color, boolean pinned) {
    public RespawnChoiceEntry(String choiceId, Component label, ResourceLocation dimension, BlockPos pos) {
        this(choiceId, label, dimension, pos, 0, false);
    }
}
