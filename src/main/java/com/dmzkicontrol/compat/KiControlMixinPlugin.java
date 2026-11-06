package com.dmzkicontrol.compat;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixins de integracao so entram quando o mod alvo esta instalado. Sem isso o Mixin tentava carregar as
 * classes do Noea/Sparking/Ultra ausentes e enchia o log de "Error loading class".
 */
public class KiControlMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String mod = null;
        if (mixinClassName.contains(".Noea")) mod = "noeabosses";
        else if (mixinClassName.contains(".Sparking")) mod = "dmzsparking";
        else if (mixinClassName.contains(".UltraBleach")) mod = "dmzultra";
        if (mod == null) return true;
        try {
            LoadingModList list = LoadingModList.get();
            return list == null || list.getModFileById(mod) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
