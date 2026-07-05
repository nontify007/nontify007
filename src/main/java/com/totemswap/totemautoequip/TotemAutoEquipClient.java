package com.totemswap.totemautoequip;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * ม็อด: เมื่อเปิดกระเป๋า (ปุ่ม E) ถ้ามือซ้าย (offhand) ไม่ใช่ Totem of Undying
 * ระบบจะหยิบ Totem จากกระเป๋ามาใส่ offhand ให้อัตโนมัติ (สลับของเดิมที่อยู่ offhand ออกไปแทน)
 *
 * เปิด/ปิดการทำงานได้ด้วยปุ่ม Right Shift (ปรับได้ในเมนู Controls ของเกม)
 *
 * เขียนด้วย Mojang official mappings สำหรับ Minecraft 26.1.2 ขึ้นไป
 */
public class TotemAutoEquipClient implements ClientModInitializer {

    public static final String MOD_ID = "totemautoequip";

    // สถานะเปิด/ปิด (ค่าเริ่มต้น = เปิด)
    public static boolean enabled = true;

    // ช่วง index ของ slot ใน AbstractContainerMenu ของกระเป๋าผู้เล่นที่นับเป็น "กระเป๋าหลัก/hotbar"
    // 0-4 = crafting, 5-8 = armor, 9-35 = main inventory, 36-44 = hotbar, 45 = offhand
    private static final int INVENTORY_SLOT_START = 9;
    private static final int INVENTORY_SLOT_END = 44; // รวม hotbar
    private static final int OFFHAND_BUTTON = 40; // ใช้กับ ClickType.SWAP เพื่อสลับไป offhand

    private static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {

        // หมวดหมู่ของปุ่มลัด (แสดงในเมนู Controls)
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "general")
        );

        // สร้างปุ่มลัดสำหรับเปิด/ปิดฟีเจอร์ (ค่าเริ่มต้น: Right Shift)
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.totemautoequip.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category
        ));

        // เช็คทุก tick ว่ามีการกดปุ่มลัดหรือยัง
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                enabled = !enabled;
                if (client.player != null) {
                    Component msg = enabled
                            ? Component.literal("§a[TotemAutoEquip] เปิดใช้งานแล้ว")
                            : Component.literal("§c[TotemAutoEquip] ปิดใช้งานแล้ว");
                    client.player.displayClientMessage(msg, true); // true = แสดงเหนือ hotbar (action bar)
                }
            }
        });

        // ทุกครั้งที่มีการเปิดหน้าจอ (screen) ใด ๆ ให้เช็คว่าเป็นหน้ากระเป๋า (E) หรือไม่
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!enabled) return;
            if (screen instanceof InventoryScreen) {
                tryEquipTotem(client);
            }
        });
    }

    /**
     * ค้นหา Totem of Undying ในกระเป๋า/hotbar แล้วสลับไปใส่ offhand
     * ถ้า offhand มี Totem อยู่แล้วจะไม่ทำอะไร
     */
    private static void tryEquipTotem(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) return;

        ItemStack offhandStack = player.getOffhandItem();
        if (offhandStack.is(Items.TOTEM_OF_UNDYING)) {
            return; // มี totem อยู่แล้ว ไม่ต้องทำอะไร
        }

        AbstractContainerMenu handler = player.containerMenu;
        if (handler == null) return;

        List<Slot> slots = handler.slots;
        for (int i = 0; i < slots.size(); i++) {
            if (i < INVENTORY_SLOT_START || i > INVENTORY_SLOT_END) {
                continue; // ข้าม crafting / armor / offhand slot เอง
            }
            Slot slot = slots.get(i);
            if (slot.getItem().is(Items.TOTEM_OF_UNDYING)) {
                // คลิก slot นี้แบบ SWAP กับปุ่ม 40 = สลับไปที่ offhand
                // (เหมือนเวลาผู้เล่นชี้ item แล้วกดปุ่ม "Swap Offhand" ในเกมจริง)
                client.gameMode.handleInventoryMouseClick(
                        handler.containerId,
                        i,
                        OFFHAND_BUTTON,
                        ClickType.SWAP,
                        player
                );
                break; // เจอและสลับแล้ว หยุดค้นหาต่อ
            }
        }
    }
}
