// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.profile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.ConfigField;
import dev.isxander.yacl3.config.v2.api.FieldAccess;
import java.lang.reflect.ParameterizedType;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public final class NativeYaclFields {
    private NativeYaclFields() {}
    public static JsonObject capture(ConfigClassHandler<?> handler, Gson gson, boolean defaults) {
        var json = new JsonObject();
        for (ConfigField<?> field : handler.fields()) field.serial().ifPresent(serial ->
            json.add(serial.serialName(), gson.toJsonTree(defaults ? field.defaultAccess().get() : field.access().get(), field.access().type())));
        return json;
    }
    public static Map<FieldAccess<?>, Object> decode(Map<ConfigField<?>, FieldAccess<?>> fields, Gson gson, JsonObject json) {
        var decoded = new LinkedHashMap<FieldAccess<?>, Object>();
        for (var entry : fields.entrySet()) {
            ConfigField<?> field = entry.getKey();
            var serial = field.serial().orElse(null);
            if (serial == null || !json.has(serial.serialName())) continue;
            var value = json.get(serial.serialName());
            var access = entry.getValue();
            if (value.isJsonNull() && !serial.nullable()) throw new IllegalArgumentException("Non-null field: " + serial.serialName());
            if (access.type() instanceof ParameterizedType type && type.getActualTypeArguments().length == 1 && type.getActualTypeArguments()[0] == Item.class) {
                if (!value.isJsonArray()) throw new IllegalArgumentException("Invalid item list: " + serial.serialName());
                for (var item : value.getAsJsonArray()) {
                    Identifier id = item.isJsonPrimitive() && item.getAsJsonPrimitive().isString() ? Identifier.tryParse(item.getAsString()) : null;
                    if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unavailable item in " + serial.serialName());
                }
            }
            Object next = gson.fromJson(value, access.type());
            if (next instanceof Number number && !Double.isFinite(number.doubleValue())) throw new IllegalArgumentException("Non-finite field: " + serial.serialName());
            decoded.put(access, next);
        }
        return decoded;
    }
    public static Map<ConfigField<?>, FieldAccess<?>> live(ConfigClassHandler<?> handler) {
        var map = new LinkedHashMap<ConfigField<?>, FieldAccess<?>>();
        for (ConfigField<?> field : handler.fields()) map.put(field, field.access());
        return map;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void apply(Map<FieldAccess<?>, Object> values) { values.forEach((field, value) -> ((FieldAccess) field).set(value)); }
}
