package com.settlement.mod.client.gui.screen

import com.mojang.blaze3d.systems.RenderSystem
import com.settlement.mod.MODID
import com.settlement.mod.screen.TradingScreenHandler
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.PressableWidget
import net.minecraft.client.render.GameRenderer
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.Text
import net.minecraft.util.Identifier

@Environment(value = EnvType.CLIENT)
class TradingScreen : HandledScreen<TradingScreenHandler> {
    constructor(handler: TradingScreenHandler, inventory: PlayerInventory, title: Text) : super(handler, inventory, title)
    public var TEXTURE: Identifier = Identifier(MODID, "textures/gui/villager.png")
    private var toggleTradeOption: ButtonWidget? = null
    private var ToggleStatus: Boolean = false
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

    private fun drawArrow(context: DrawContext, i: Int, j: Int) {
        context.drawTexture(TEXTURE, i + 74, j + 40, 176, 0, 28, 20)
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false)
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        RenderSystem.setShader(GameRenderer::getPositionTexProgram)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.setShaderTexture(0, TEXTURE)
        var i: Int = (this.width - this.backgroundWidth) / 2
        var j: Int = (this.height - this.backgroundHeight) / 2
        context.drawTexture(TEXTURE, i, j, 0, 0, this.backgroundWidth, this.backgroundHeight)
        drawArrow(context, i, j)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)
    }

    @Environment(value = EnvType.CLIENT)
    class OptionButton(
        private val x: Int,
        private val y: Int,
        private val texture: Identifier,
    ) :
        PressableWidget(x, y, 52, 8, ScreenTexts.EMPTY) {
        private var selling: Boolean = false
        init {
            this.selling = false
        }
        public fun getTexture(): Identifier {
            return this.texture
        }
        public fun isSelling(): Boolean {
            return this.selling
        }
        override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
            context.drawTexture(getTexture(), x, y, 176, 42, 52, 8)
            if (!isSelling()) {
                context.drawTexture(getTexture(), x + 22, y + 2, 181, 73, 7, 5)
            } else {
                context.drawTexture(getTexture(), x + 22, y + 2, 188, 73, 7, 5)
            }
        }
        override fun onPress() {
            toggleOperation()
        }
        public fun getOperation(): Boolean {
            return this.selling
        }
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
    ) :
        PressableWidget(x, y, 16, 7, ScreenTexts.EMPTY) {
        private var index: Int? = null
        private var disabled: Boolean = false
        private var locked: Boolean = false
        init {
            this.index = index
        }
        public fun getTexture(): Identifier {
            return this.texture
        }

        public fun getIndex(): Int {
            return this.index!!
        }
        override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
            if (isLocked()) {
                context.drawTexture(getTexture(), x, y, 176, 66, 16, 7)
                context.drawTexture(getTexture(), x + 6, y + 2, 176, 79, 3, 3)
            } else {
                context.drawTexture(getTexture(), x, y, 176, 66, 16, 7)
                context.drawTexture(getTexture(), x + 6, y + 2, 176, 81, 3, 3)
            }
        }
        override fun onPress() {
            if (isDisabled()) {
                return
            }
            toggleLocked()
        }
        public fun isLocked(): Boolean {
            return this.locked
        }
        public fun toggleLocked() {
            this.locked = !locked
        }

        public fun isDisabled(): Boolean {
            return this.disabled
        }

        public fun setDisabled(disabled: Boolean) {
            this.disabled = disabled
        }
        override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
            this.appendDefaultNarrations(builder)
        }
    }
}
