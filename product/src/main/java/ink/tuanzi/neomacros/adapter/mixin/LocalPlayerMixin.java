package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.SignEditHub;
import ink.tuanzi.neomacros.port.model.SignEditData;
import ink.tuanzi.neomacros.port.model.SignEditVerdict;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Feeds the sign-edit source: the server's sign-editor packet opens the
 * editor through LocalPlayer.openTextEdit, and the HEAD injection lets sync
 * subscribers replace the four lines (written back onto the block entity so
 * the editor shows the replacement) or skip the editor entirely, submitting
 * the possibly replaced text to the server directly.
 */
@Mixin(net.minecraft.client.player.LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Shadow
    @Final
    public net.minecraft.client.multiplayer.ClientPacketListener connection;

    //? if >=26.3 {
    @Inject(method = "openTextEdit", at = @At("HEAD"), cancellable = true)
    private void neomacros$onOpenSignEditScreen(SignBlockEntity sign,
            net.minecraft.world.level.block.entity.SignTextSlot slot, CallbackInfo ci) {
        boolean front = slot == net.minecraft.world.level.block.entity.SignTextSlot.FRONT;
        var original = readLines(sign, front);
        var pos = sign.getBlockPos();
        SignEditVerdict verdict = SignEditHub.fire(new SignEditData(
                pos.getX(), pos.getY(), pos.getZ(), front, original));
        List<String> lines = normalize(verdict, original);
        if (!lines.equals(original)) {
            var mutable = sign.getText(slot).asMutable();
            for (int i = 0; i < 4; i++) {
                mutable.setLine(i, net.minecraft.network.chat.Component.literal(lines.get(i)));
            }
            sign.setText(mutable.asImmutable(), slot);
        }
        if (verdict.canceled() || verdict.closeScreen()) {
            connection.send(new net.minecraft.network.protocol.game.ServerboundSignUpdatePacket(
                    pos, List.copyOf(lines), slot));
            ci.cancel();
        }
    }
    //?} else {
    /*
    @Inject(method = "openTextEdit", at = @At("HEAD"), cancellable = true)
    private void neomacros$onOpenSignEditScreen(SignBlockEntity sign, boolean front,
            CallbackInfo ci) {
        var original = readLines(sign, front);
        var pos = sign.getBlockPos();
        SignEditVerdict verdict = SignEditHub.fire(new SignEditData(
                pos.getX(), pos.getY(), pos.getZ(), front, original));
        List<String> lines = normalize(verdict, original);
        if (!lines.equals(original)) {
            var text = sign.getText(front);
            for (int i = 0; i < 4; i++) {
                text = text.setMessage(i,
                        net.minecraft.network.chat.Component.literal(lines.get(i)));
            }
            sign.setText(text, front);
        }
        if (verdict.canceled() || verdict.closeScreen()) {
            connection.send(new net.minecraft.network.protocol.game.ServerboundSignUpdatePacket(
                    pos, front, lines.get(0), lines.get(1), lines.get(2), lines.get(3)));
            ci.cancel();
        }
    }
    */
    //?}

    /** Reads the sign face's current messages as plain strings. */
    private static List<String> readLines(SignBlockEntity sign, boolean front) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        //? if >=26.3 {
        var text = sign.getText(front
                ? net.minecraft.world.level.block.entity.SignTextSlot.FRONT
                : net.minecraft.world.level.block.entity.SignTextSlot.BACK);
        //?} else {
        /*
        var text = sign.getText(front);
        */
        //?}
        List<String> lines = new ArrayList<>(4);
        for (var message : text.getMessages(minecraft.isTextFilteringEnabled())) {
            lines.add(message.getString());
        }
        return lines;
    }

    /** The verdict lines, or the original text, padded/truncated to four. */
    private static List<String> normalize(SignEditVerdict verdict, List<String> original) {
        List<String> lines = new ArrayList<>(verdict.lines() != null ? verdict.lines() : original);
        while (lines.size() < 4) {
            lines.add("");
        }
        return lines.size() > 4 ? lines.subList(0, 4) : lines;
    }
}
