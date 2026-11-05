// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import io.github.arcadiumclient.runtime.client.optimizations.PlayerPoseMath;
import io.github.arcadiumclient.runtime.client.optimizations.PlayerPoseOptions;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.PlayerModel;

public final class AnimationPose072Qa {
    private AnimationPose072Qa() {}

    public static Map<String, Object> verify(Minecraft minecraft) throws ReflectiveOperationException {
        if (minecraft.player == null) throw new IllegalStateException("Animation QA needs a loaded world");
        Class<?> type = Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig");
        Object config = type.getMethod("instance").invoke(null);
        Object movement = type.getField("movement").get(config), other = type.getField("other").get(config);
        Field wavy = movement.getClass().getField("wavyArms"), sneak = movement.getClass().getField("sneakAnimation");
        Field blocking = other.getClass().getField("thirdPersonSwordBlockingPosition");
        Field dinnerbone = movement.getClass().getField("dinnerboneMode"), invertedEntities = movement.getClass().getField("dinnerboneModeEntities");
        boolean oldWavy = wavy.getBoolean(movement), oldBlocking = blocking.getBoolean(other);
        boolean oldDinnerbone = dinnerbone.getBoolean(movement), oldEntities = invertedEntities.getBoolean(movement);
        Object oldSneak = sneak.get(movement);
        int checks = 0, variants = 0;
        float maximumError = 0;
        try {
            for (boolean waves : new boolean[]{false, true}) {
                for (boolean oldBlock : new boolean[]{false, true}) {
                    for (String sneakStyle : new String[]{"VANILLA", "V1_7"}) {
                        wavy.setBoolean(movement, waves); blocking.setBoolean(other, oldBlock);
                        sneak.set(movement, enumValue(sneak.getType(), sneakStyle));
                        PlayerPoseOptions.refresh();
                        require(PlayerPoseOptions.compatible(), "Supported animation mode rejected");
                        int flags = (waves ? PlayerPoseOptions.WAVY_ARMS : 0) |
                                (oldBlock ? PlayerPoseOptions.OLD_BLOCKING : 0) |
                                (sneakStyle.equals("V1_7") ? PlayerPoseOptions.OLD_SNEAK : 0);
                        require(PlayerPoseOptions.flags() == flags, "Native animation flags differ");
                        for (boolean slim : new boolean[]{false, true}) {
                            PlayerModel model = new PlayerModel(0, slim), rest = new PlayerModel(0, slim);
                            ModelPart[] body = parts(model), defaults = parts(rest);
                            Random random = new Random(72072);
                            float[] actual = new float[6];
                            for (int sample = 0; sample < 256; sample++) {
                                float walk = random.nextFloat() * 40, speed = random.nextFloat(), age = random.nextFloat() * 1200;
                                float yaw = random.nextFloat() * 200 - 100, pitch = random.nextFloat() * 140 - 70;
                                float swing = random.nextFloat();
                                model.attackAnimationProgress = swing; model.itemInLeftHand = sample % 2; model.itemInRightHand = sample % 4;
                                model.sneaking = sample % 5 == 0; model.riding = sample % 7 == 0; model.aimingBow = sample % 11 == 0;
                                model.setupAnimation(walk, speed, age, yaw, pitch, .0625f, minecraft.player);
                                for (int part = 0; part < body.length; part++) {
                                    ModelPart original = body[part], template = defaults[part];
                                    PlayerPoseMath.part(walk, speed, age, yaw, pitch, swing, model.itemInLeftHand, model.itemInRightHand,
                                            model.sneaking, model.riding, model.aimingBow, PlayerPoseMath.BONES[part],
                                            template.x, template.y, template.z, flags, actual);
                                    float[] expected = {original.x, original.y, original.z, original.rotationX, original.rotationY, original.rotationZ};
                                    for (int value = 0; value < expected.length; value++) {
                                        float error = Math.abs(expected[value] - actual[value]);
                                        maximumError = Math.max(maximumError, error);
                                        require(error < .00002f, "Native pose differs: waves=" + waves + " blocking=" + oldBlock +
                                                " sneak=" + sneakStyle + " slim=" + slim + " sample=" + sample +
                                                " part=" + part + " value=" + value + " expected=" + expected[value] + " actual=" + actual[value]);
                                        checks++;
                                    }
                                }
                            }
                            variants++;
                        }
                    }
                }
            }
            dinnerbone.setBoolean(movement, false); invertedEntities.setBoolean(movement, false); PlayerPoseOptions.refresh();
            require(PlayerPoseOptions.upsideDown("Dinnerbone", true), "Native Dinnerbone pose missing");
            require(!PlayerPoseOptions.upsideDown("Dinnerbone", false), "Cape visibility was ignored");
            for (Field toggle : new Field[]{dinnerbone, invertedEntities}) {
                toggle.setBoolean(movement, true); PlayerPoseOptions.refresh();
                require(PlayerPoseOptions.compatible(), "Supported inverted pose rejected");
                require(PlayerPoseOptions.upsideDown("ArcadiumTest", false), "Configured inverted pose missing");
                toggle.setBoolean(movement, false);
            }
        } finally {
            wavy.setBoolean(movement, oldWavy); blocking.setBoolean(other, oldBlocking); sneak.set(movement, oldSneak);
            dinnerbone.setBoolean(movement, oldDinnerbone); invertedEntities.setBoolean(movement, oldEntities);
            PlayerPoseOptions.refresh();
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("passed", true); report.put("nativeVariants", variants); report.put("nativeValuesCompared", checks);
        report.put("maximumPoseError", maximumError); report.put("wavyArms", true); report.put("oldBlocking", true);
        report.put("oldSneak", true); report.put("bowPriority", true); report.put("invertedPlayers", true);
        return report;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> type, String name) { return Enum.valueOf((Class) type, name); }
    private static ModelPart[] parts(PlayerModel model) {
        return new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg,
                model.hat, model.leftPants, model.rightPants, model.leftSleeve, model.rightSleeve, model.jacket};
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
