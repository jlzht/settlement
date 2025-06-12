package com.settlement.mod.client.gui.screen

import com.settlement.mod.MODID
import com.settlement.mod.screen.TradingScreenHandler
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.PressableWidget
import net.minecraft.client.render.RenderLayer
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.Text
import net.minecraft.util.Identifier

@Environment(value = EnvType.CLIENT)
class TradingScreen : HandledScreen<TradingScreenHandler> {
    constructor(handler: TradingScreenHandler, inventory: PlayerInventory, title: Text) : super(handler, inventory, title)

    val TEXTURE = Identifier.of(MODID, "textures/gui/villager.png")
    private var toggleTradeOption: ButtonWidget? = null
    private var toggleStatus: Boolean = false
    private var locks: Array<LockButton?> = arrayOfNulls<LockButton>(3)
    private var option: OptionButton? = null

    override fun init() {
        var x: Int = (this.width - this.backgroundWidth) / 2
        var y: Int = (this.height - this.backgroundHeight) / 2
        for (i in 0..2) {
            this.locks[i] = LockButton(x + 107 + 18 * i, y + 34, TEXTURE, i)
            addDrawableChild(locks[i])
        }
        option = OptionButton(x + 16, y + 24, TEXTURE)
        addDrawableChild(option)
        super.init()
    }

    private fun drawArrow(
        context: DrawContext,
        i: Int,
        j: Int,
    ) {
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, (i + 74), (j + 40), 176.0f, 21.0f, 28, 21, 256, 256)
    }

    override fun drawForeground(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
    ) {
        context.drawText(
            this.textRenderer,
            this.playerInventoryTitle,
            this.playerInventoryTitleX,
            this.playerInventoryTitleY,
            0x404040,
            false,
        )
    }

    override fun drawBackground(
        context: DrawContext,
        delta: Float,
        mouseX: Int,
        mouseY: Int,
    ) {
        val i = (this.width - this.backgroundWidth) / 2
        val j = (this.height - this.backgroundHeight) / 2
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, i, j, 0.0f, 0.0f, this.backgroundWidth, this.backgroundHeight, 256, 256)
        drawArrow(context, i, j)
    }

    override fun render(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)
    }

    @Environment(value = EnvType.CLIENT)
    class OptionButton(
        private val x: Int,
        private val y: Int,
        private val texture: Identifier,
    ) : PressableWidget(x, y, 52, 8, ScreenTexts.EMPTY) {
        private var selling: Boolean = false

        init {
            this.selling = false
        }

        public fun getTexture(): Identifier = this.texture

        public fun isSelling(): Boolean = this.selling

        override fun renderWidget(
            context: DrawContext,
            mouseX: Int,
            mouseY: Int,
            delta: Float,
        ) {
            context.drawTexture(RenderLayer::getGuiTextured, getTexture(), x, y, 176.0f, 42.0f, 52, 8, 256, 256)
            if (!isSelling()) {
                context.drawTexture(RenderLayer::getGuiTextured, getTexture(), x + 22, y + 2, 181.0f, 73.0f, 7, 4, 256, 256)
            } else {
                context.drawTexture(RenderLayer::getGuiTextured, getTexture(), x + 22, y + 2, 188.0f, 73.0f, 7, 4, 256, 256)
            }
        }

        override fun onPress() {
            toggleOperation()
        }

        public fun getOperation(): Boolean = this.selling

        public fun toggleOperation() {
            this.selling = !selling
        }

        override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
            this.appendDefaultNarrations(builder)
        }
    }

    @Environment(value = EnvType.CLIENT)
    class LockButton(
        private val x: Int,
        private val y: Int,
        private val texture: Identifier,
        index: Int,
    ) : PressableWidget(x, y, 16, 7, ScreenTexts.EMPTY) {
        private var index: Int? = null
        private var disabled: Boolean = false
        private var locked: Boolean = false

        init {
            this.index = index
        }

        public fun getTexture(): Identifier = this.texture

        public fun getIndex(): Int = this.index!!

        override fun renderWidget(
            context: DrawContext,
            mouseX: Int,
            mouseY: Int,
            delta: Float,
        ) {
            context.drawTexture(RenderLayer::getGuiTextured, getTexture(), x, y, 176.0f, 66.0f, 16, 7, 256, 256)
            if (isLocked()) {
                context.drawTexture(RenderLayer::getGuiTextured, getTexture(), x + 6, y + 2, 179.0f, 78.0f, 3, 3, 256, 256)
            } else {
                context.drawTexture(RenderLayer::getGuiTextured, getTexture(), x + 6, y + 2, 176.0f, 78.0f, 3, 3, 256, 256)
            }
        }

        override fun onPress() {
            if (isDisabled()) {
                return
            }
            toggleLocked()
        }

        public fun isLocked(): Boolean = this.locked

        public fun toggleLocked() {
            this.locked = !locked
        }

        public fun isDisabled(): Boolean = this.disabled

        public fun setDisabled(disabled: Boolean) {
            this.disabled = disabled
        }

        override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
            this.appendDefaultNarrations(builder)
        }
    }
}
