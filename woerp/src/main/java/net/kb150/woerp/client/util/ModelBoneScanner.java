package net.kb150.woerp.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;

import java.lang.reflect.Field;
import java.util.*;

public class ModelBoneScanner {
    private static Field cachedChildrenField;
    private static boolean childrenFieldResolved = false;

    public static boolean applyBoneTransforms(EntityModel<?> model, String bonePath, PoseStack poseStack) {
        if (model == null || bonePath == null || bonePath.trim().isEmpty()) return false;

        if ("head".equalsIgnoreCase(bonePath) && model instanceof HeadedModel headed) {
            headed.getHead().translateAndRotate(poseStack);
            return true;
        }

        String[] pathParts = bonePath.split("/");
        ModelPart currentPart = null;

        // HierarchicalModel haelt Kindkuben in einer Map unter unverschleierten String-Namen
        if (model instanceof HierarchicalModel<?> hierarchical) {
            ModelPart root = hierarchical.root();
            if (pathParts[0].equals("root")) currentPart = root;
            else if (root.hasChild(pathParts[0])) currentPart = root.getChild(pathParts[0]);
        }

        if (currentPart == null) {
            Class<?> clazz = model.getClass();
            while (clazz != null && clazz != Object.class) {
                try {
                    Field field = clazz.getDeclaredField(pathParts[0]);
                    field.setAccessible(true);
                    currentPart = (ModelPart) field.get(model);
                    if (currentPart != null) break;
                } catch (Exception ignored) {}
                clazz = clazz.getSuperclass();
            }
        }

        if (currentPart == null) return false;
        currentPart.translateAndRotate(poseStack);

        for (int i = 1; i < pathParts.length; i++) {
            Map<String, ModelPart> children = getChildrenOf(currentPart);
            if (children != null && children.containsKey(pathParts[i])) {
                currentPart = children.get(pathParts[i]);
                currentPart.translateAndRotate(poseStack);
            } else {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, ModelPart> getChildrenOf(ModelPart part) {
        if (!childrenFieldResolved) {
            childrenFieldResolved = true;
            for (Field field : ModelPart.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    cachedChildrenField = field;
                    break;
                }
            }
        }
        if (cachedChildrenField == null) return Collections.emptyMap();
        try {
            return (Map<String, ModelPart>) cachedChildrenField.get(part);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public static Map<String, ModelPart> getSafeBones(EntityModel<?> model) {
        Map<String, ModelPart> result = new HashMap<>();
        if (model == null) return result;

        if (model instanceof HierarchicalModel<?> hierarchical) {
            ModelPart root = hierarchical.root();
            result.put("root", root);
            scanSafeChildren("root", root, result, 0);
        }

        Map<String, ModelPart> fieldParts = new HashMap<>();
        for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (ModelPart.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        ModelPart p = (ModelPart) f.get(model);
                        if (p != null) fieldParts.putIfAbsent(f.getName(), p);
                    } catch (Exception ignored) {}
                }
            }
        }

        for (Map.Entry<String, ModelPart> entry : fieldParts.entrySet()) {
            result.putIfAbsent(entry.getKey(), entry.getValue());
            scanSafeChildren(entry.getKey(), entry.getValue(), result, 0);
        }
        return result;
    }

    private static void scanSafeChildren(String prefix, ModelPart parent, Map<String, ModelPart> result, int depth) {
        if (depth > 8) return;
        for (Map.Entry<String, ModelPart> e : getChildrenOf(parent).entrySet()) {
            if (e.getValue() != null) {
                String path = prefix + "/" + e.getKey();
                result.putIfAbsent(path, e.getValue());
                scanSafeChildren(path, e.getValue(), result, depth + 1);
            }
        }
    }
}