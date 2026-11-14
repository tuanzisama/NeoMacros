package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Live handle for an {@code imageButton} control: the textured variant of
 * {@link Button} (NeoMacros models it as a Button subtype). Sprite
 * members stay on the builder side ({@code TexturedButtonBuilder});
 * the shared widget face arrives with G-036B.
 *
 * @zh {@code imageButton} 控件的活句柄：{@link Button} 的贴图变体（NeoMacros 以 Button 子类建模）。贴图字段在构建器侧（{@code TexturedButtonBuilder}），共享成员面随 G-036B 到来。
 */
@ApiClass(name = "ImageButton", kind = ApiClassKind.HANDLE,
        wraps = "net.minecraft.client.gui.components.ImageButton")
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public class ImageButton extends Button {

    ImageButton(BuiltScreen screen, String id) {
        super(screen, id);
    }
}
