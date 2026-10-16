package com.customcosmetics.gametest;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.player.RemotePlayer;
import java.util.UUID;
final class SharingModelFixture {
    static RemotePlayer player(net.minecraft.client.Minecraft client,UUID uuid,String name){
        var body=texture(client,"fixture-skin",64,64,0xFFEE55DD,0xFFAA33BB);
        var cape=texture(client,"fixture-cape",64,32,0xFF33FF44,0xFF119922);
        var skin=new net.minecraft.world.entity.player.PlayerSkin(body,cape,cape,net.minecraft.world.entity.player.PlayerModelType.WIDE,true);
        var player=new RemotePlayer(client.level,new GameProfile(uuid,name)){
            @Override public net.minecraft.world.entity.player.PlayerSkin getSkin(){return skin;}
            @Override public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part){return true;}
            @Override public boolean isCurrentlyGlowing(){return hasGlowingTag()||super.isCurrentlyGlowing();}
            @Override public int getTeamColor(){return hasGlowingTag()?0x33FFFF:super.getTeamColor();}
        };
        var helmet=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET);
        var sword=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
        helmet.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
        sword.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,helmet);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,sword);
        return player;
    }
    private static net.minecraft.core.ClientAsset.Texture texture(net.minecraft.client.Minecraft client,String name,int width,int height,int a,int b){
        var id=net.minecraft.resources.Identifier.fromNamespaceAndPath("customcosmetics",name);
        var image=new com.mojang.blaze3d.platform.NativeImage(width,height,false);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)image.setPixel(x,y,(x+y)%2==0?a:b);
        client.getTextureManager().register(id,new net.minecraft.client.renderer.texture.DynamicTexture(()->name,image));
        return new net.minecraft.core.ClientAsset.Texture(){public net.minecraft.resources.Identifier id(){return id;}public net.minecraft.resources.Identifier texturePath(){return id;}};
    }
}
