package net.kb150.dragoncolonies.client.gui;

import com.ldtteam.blockui.BOGuiGraphics;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.ScrollingList;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.network.DragonColoniesNetwork;
import net.kb150.dragoncolonies.network.message.AcceptExportOfferMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WindowDragonExport extends BOWindow {
    private final BlockPos roostPos;
    private final UUID dragonId;
    private final CompoundTag offersTag;
    
    private ListTag offersList;
    private int selectedIndex = -1;
    private final List<ItemStack> currentRenderItems = new ArrayList<>();

    public WindowDragonExport(BlockPos roostPos, UUID dragonId, CompoundTag offersTag) {
        super(new ResourceLocation(DragonColonies.MOD_ID, "gui/dragon_export.xml"));
        this.roostPos = roostPos;
        this.dragonId = dragonId;
        this.offersTag = offersTag;
    }

    @Override
    public void onOpened() {
        super.onOpened();
        this.offersList = offersTag.getList("Offers", Tag.TAG_COMPOUND);
		Text titleText = this.findPaneOfTypeByID("txt_title", Text.class);
			if (titleText != null && offersTag.contains("DragonDisplayName")) {
				String rawName = offersTag.getString("DragonDisplayName");
				
				Component nameComp;
				try {
					nameComp = Component.Serializer.fromJson(rawName);
					if (nameComp == null) nameComp = Component.literal(rawName);
				} catch (Exception e) {
					nameComp = Component.literal(rawName);
				}

				titleText.setText(Component.translatable("dragoncolonies.gui.dragon_export.title_with_dragon", nameComp));
			}       
        Button btnClose = this.findPaneOfTypeByID("btn_close", Button.class);
        if (btnClose != null) btnClose.setHandler(b -> this.close());
        
        ScrollingList list = this.findPaneOfTypeByID("list_offers", ScrollingList.class);
        if (list != null) {
            list.setDataProvider(new ScrollingList.DataProvider() {
                @Override
                public int getElementCount() { return offersList.size(); }
                
                @Override
                public void updateElement(int index, @NotNull Pane rowPane) {
                    CompoundTag offer = offersList.getCompound(index);
                    Button btn = rowPane.findPaneOfTypeByID("btn_faction", Button.class);
                    Image img = rowPane.findPaneOfTypeByID("img_faction_icon", Image.class);
                    Text lbl = rowPane.findPaneOfTypeByID("lbl_faction_name", Text.class);
                    
                    if (lbl != null) {
                        lbl.setText(Component.literal(offer.getString("FactionName")));
                        lbl.setEnabled(false);
                    }
                    if (img != null) {
                        img.setImage(new ResourceLocation(offer.getString("Icon")), false);
                    }
                    if (btn != null) {
                        btn.setHandler(b -> selectOffer(index));
                    }
                }
            });
        }
        
        Button btnAccept = this.findPaneOfTypeByID("btn_accept", Button.class);
        if (btnAccept != null) {
            btnAccept.setHandler(b -> {
                if (selectedIndex >= 0) {
                    CompoundTag offer = offersList.getCompound(selectedIndex);
                    DragonColoniesNetwork.CHANNEL.sendToServer(new AcceptExportOfferMessage(roostPos, dragonId, offer.getString("FactionId")));
                    this.close();
                }
            });
        }
    }

    private void selectOffer(int index) {
        this.selectedIndex = index;
        CompoundTag offer = offersList.getCompound(index);
        
        Image img = this.findPaneOfTypeByID("detail_faction_img", Image.class);
        if (img != null) img.setImage(new ResourceLocation(offer.getString("Icon")), false);
        
        Text name = this.findPaneOfTypeByID("detail_faction_name", Text.class);
        if (name != null) name.setText(Component.literal(offer.getString("FactionName")));
        
        Text flavor = this.findPaneOfTypeByID("detail_flavor_text", Text.class);
        if (flavor != null) flavor.setText(Component.literal(offer.getString("Text")));
        
        Button btnAccept = this.findPaneOfTypeByID("btn_accept", Button.class);
        if (btnAccept != null) btnAccept.setEnabled(true);
        
        currentRenderItems.clear();
        ListTag itemsTag = offer.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < itemsTag.size(); i++) {
            currentRenderItems.add(ItemStack.of(itemsTag.getCompound(i)));
        }
    }

    @Override
    public void draw(BOGuiGraphics guiGraphics, double mouseX, double mouseY) {
        super.draw(guiGraphics, mouseX, mouseY);
        
        Pane detailContainer = this.findPaneByID("detail_container");
        if (detailContainer == null || selectedIndex < 0) return;

        int containerX = this.getX() + detailContainer.getX();
        int containerY = this.getY() + detailContainer.getY();
        
        int itemsPerRow = 10;
        int spacingX = 18; // Dicht gepackt (16px Item + 2px Rand)
        int spacingY = 20; // Zeilenumbruch
        
        // Speichere den Tooltip, um ihn erst am Ende zu rendern (damit er im Vordergrund liegt)
        ItemStack tooltipStack = null;
        int tooltipX = 0, tooltipY = 0;
        
        for (int i = 0; i < currentRenderItems.size(); i++) {
            int row = i / itemsPerRow;
            int col = i % itemsPerRow;
            
            // Berechne, wie viele Items in der aktuellen Zeile sind (für saubere Zentrierung)
            int itemsInThisRow = Math.min(currentRenderItems.size() - (row * itemsPerRow), itemsPerRow);
            int rowWidth = itemsInThisRow * spacingX;
            
            // Zentriere die aktuelle Zeile in der Mitte des Detail-Containers (Breite ca. 250px -> Mitte bei 125)
            int startX = containerX + 125 - (rowWidth / 2);
            
            int slotX = startX + (col * spacingX);
            // StartY etwas nach oben geschoben (105), um Platz für bis zu 3-4 Zeilen zu haben
            int slotY = containerY + 105 + (row * spacingY); 
            
            guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0x88000000);
            guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x44FFFFFF);
            
            ItemStack stack = currentRenderItems.get(i);
            guiGraphics.renderItem(stack, slotX, slotY);
            guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, slotX, slotY);
            
            // Kollisionsprüfung für die Maus
            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                tooltipStack = stack;
                tooltipX = (int) mouseX;
                tooltipY = (int) mouseY;
            }
        }
        
        // Render den Hover-Text der Maus erst ganz zum Schluss
        if (tooltipStack != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 500.0F);
            guiGraphics.renderTooltip(Minecraft.getInstance().font, tooltipStack, tooltipX, tooltipY);
            guiGraphics.flush();
            guiGraphics.pose().popPose();
        }
    }
}